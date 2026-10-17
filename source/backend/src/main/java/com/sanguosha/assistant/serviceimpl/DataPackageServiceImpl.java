package com.sanguosha.assistant.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.assistant.entity.General;
import com.sanguosha.assistant.service.AppSettingService;
import com.sanguosha.assistant.service.DataPackageService;
import com.sanguosha.assistant.service.GeneralService;
import com.sanguosha.assistant.service.IdentityModeService;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.AppSettingsRequest;
import com.sanguosha.assistant.vo.AppSettingsVO;
import com.sanguosha.assistant.vo.DataPackageImportResult;
import com.sanguosha.assistant.vo.IdentityModeRequest;
import com.sanguosha.assistant.vo.IdentityModeRuleRequest;
import com.sanguosha.assistant.vo.IdentityModeRuleVO;
import com.sanguosha.assistant.vo.IdentityModeVO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DataPackageServiceImpl implements DataPackageService {
    private static final String PACKAGE_FORMAT = "sgs-data-package";
    private static final int PACKAGE_VERSION = 1;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String IMAGE_PREFIX = "images/";
    private static final int MAX_GENERAL_COUNT = 1_000;
    private static final int MAX_IDENTITY_MODE_COUNT = 50;
    private static final int MAX_IMAGE_COUNT = 1_000;
    private static final long MAX_MANIFEST_BYTES = 1_024L * 1_024L;
    private static final long MAX_IMAGE_BYTES = 1_024L * 1_024L;
    private static final long MAX_ARCHIVE_BYTES = 600L * 1_024L * 1_024L;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1_024L * 1_024L;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final GeneralService generalService;
    private final IdentityModeService identityModeService;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build();

    @Value("${sgs.upload.dir}")
    private String uploadDir;

    @Value("${sgs.shared-hall.verification-url:}")
    private String sharedHallVerificationUrl;

    @Override
    public void exportPackage(OutputStream outputStream) throws IOException {
        List<General> generals = generalService.allGenerals();
        Map<Long, Path> imagePaths = new HashMap<>();
        for (General general : generals) {
            imagePaths.put(general.getId(), resolveGeneralImage(general));
        }

        DataPackageManifest manifest = new DataPackageManifest();
        manifest.setFormat(PACKAGE_FORMAT);
        manifest.setVersion(PACKAGE_VERSION);
        manifest.setExportedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
        manifest.setSettings(toSettingsRequest(appSettingService.getSettings()));
        manifest.setIdentityModes(identityModeService.listModes(true).stream().map(this::toModeRequest).toList());
        List<PackageGeneral> packageGenerals = new ArrayList<>();
        for (General general : generals) {
            PackageGeneral item = PackageGeneral.from(general);
            item.setImageFile(IMAGE_PREFIX + safeImageFilename(general));
            packageGenerals.add(item);
        }
        manifest.setGenerals(packageGenerals);

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            writeZipEntry(zip, MANIFEST_ENTRY, objectMapper.writeValueAsBytes(manifest));
            for (int index = 0; index < generals.size(); index += 1) {
                General general = generals.get(index);
                PackageGeneral item = packageGenerals.get(index);
                zip.putNextEntry(new ZipEntry(item.getImageFile()));
                Files.copy(imagePaths.get(general.getId()), zip);
                zip.closeEntry();
            }
        }
    }

    @Override
    @Transactional
    public DataPackageImportResult importPackage(MultipartFile file, boolean allowUnverified) {
        if (file == null || file.isEmpty()) throw new AppException("请选择数据包 ZIP 文件");
        if (file.getSize() > MAX_ARCHIVE_BYTES) throw new AppException("数据包压缩文件超过 600MB 限制");
        String actualSha256 = calculateSha256(file);
        boolean sharedPackageVerified = verifySharedPackage(actualSha256, allowUnverified);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sgs-data-package-");
            ImportedPackage imported = unpackPackage(file, tempDir);
            validateManifest(imported.manifest());
            Map<String, List<General>> existingGeneralsByName = new HashMap<>();
            for (General general : generalService.allGenerals()) {
                existingGeneralsByName.computeIfAbsent(generalNameKey(general.getName()), ignored -> new ArrayList<>()).add(general);
            }
            Map<String, Integer> importedGeneralCounts = new HashMap<>();
            int createdGenerals = 0;
            int updatedGenerals = 0;
            for (PackageGeneral general : imported.manifest().getGenerals()) {
                Path image = imported.images().get(general.getImageFile());
                if (image == null) throw new AppException("缺少武将图片：" + general.getName());
                String nameKey = generalNameKey(general.getName());
                int occurrence = importedGeneralCounts.merge(nameKey, 1, Integer::sum) - 1;
                List<General> matchingGenerals = existingGeneralsByName.getOrDefault(nameKey, List.of());
                String initialHp = general.getInitialHp() == null ? "" : String.valueOf(general.getInitialHp());
                String maxHp = general.getMaxHp() == null ? "" : String.valueOf(general.getMaxHp());
                String initialArmor = general.getInitialArmor() == null ? "" : String.valueOf(general.getInitialArmor());
                MultipartFile packageImage = new PathMultipartFile(image);
                if (occurrence < matchingGenerals.size()) {
                    generalService.updateGeneral(matchingGenerals.get(occurrence).getId(), general.getName(), general.getFaction(), general.getIsLord(), general.getStartsHidden(), initialHp, maxHp, initialArmor, "", packageImage.getOriginalFilename(), packageImage);
                    updatedGenerals += 1;
                } else {
                    generalService.createGeneral(general.getName(), general.getFaction(), general.getIsLord(), general.getStartsHidden(), initialHp, maxHp, initialArmor, "", packageImage.getOriginalFilename(), packageImage);
                    createdGenerals += 1;
                }
            }
            int createdModes = 0;
            int updatedModes = 0;
            Map<String, IdentityModeVO> existingModes = new HashMap<>();
            for (IdentityModeVO mode : identityModeService.listModes(true)) existingModes.put(modeNameKey(mode.getName()), mode);
            for (IdentityModeRequest mode : imported.manifest().getIdentityModes()) {
                IdentityModeVO existing = existingModes.get(modeNameKey(mode.getName()));
                if (existing == null) {
                    identityModeService.createMode(mode);
                    createdModes += 1;
                } else {
                    identityModeService.updateMode(existing.getId(), mode);
                    updatedModes += 1;
                }
            }
            appSettingService.updateSettings(imported.manifest().getSettings());
            return new DataPackageImportResult(createdGenerals, updatedGenerals, createdModes, updatedModes, sharedPackageVerified);
        } catch (IOException exception) {
            throw new AppException("读取数据包失败");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private ImportedPackage unpackPackage(MultipartFile file, Path tempDir) throws IOException {
        DataPackageManifest manifest = null;
        Map<String, Path> images = new HashMap<>();
        long totalBytes = 0;
        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (MANIFEST_ENTRY.equals(name)) {
                    if (manifest != null) throw new AppException("数据包包含重复清单");
                    byte[] bytes = readEntry(zip, MAX_MANIFEST_BYTES, totalBytes);
                    totalBytes += bytes.length;
                    manifest = objectMapper.readValue(bytes, DataPackageManifest.class);
                } else if (isImageEntry(name)) {
                    if (images.size() >= MAX_IMAGE_COUNT || images.containsKey(name)) throw new AppException("数据包图片数量异常");
                    Path target = tempDir.resolve(name).normalize();
                    if (!target.startsWith(tempDir)) throw new AppException("数据包路径不合法");
                    Files.createDirectories(target.getParent());
                    long written = copyEntry(zip, target, MAX_IMAGE_BYTES, MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes);
                    totalBytes += written;
                    images.put(name, target);
                } else {
                    throw new AppException("数据包包含不支持的文件");
                }
                if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) throw new AppException("数据包解压后超过 512MB 限制");
                zip.closeEntry();
            }
        }
        if (manifest == null) throw new AppException("数据包缺少 manifest.json");
        return new ImportedPackage(manifest, images);
    }

    private void validateManifest(DataPackageManifest manifest) {
        if (manifest == null || !PACKAGE_FORMAT.equals(manifest.getFormat()) || manifest.getVersion() == null || manifest.getVersion() != PACKAGE_VERSION) throw new AppException("不支持的数据包格式");
        if (manifest.getSettings() == null) throw new AppException("数据包缺少系统设置");
        if (manifest.getGenerals() == null || manifest.getGenerals().size() > MAX_GENERAL_COUNT) throw new AppException("数据包武将数量异常");
        if (manifest.getIdentityModes() == null || manifest.getIdentityModes().size() > MAX_IDENTITY_MODE_COUNT) throw new AppException("数据包身份模式数量异常");
        Set<String> imageFiles = new HashSet<>();
        for (PackageGeneral general : manifest.getGenerals()) {
            if (general == null || general.getName() == null || general.getName().isBlank()) throw new AppException("数据包武将名称异常");
            if (!isImageEntry(general.getImageFile()) || !imageFiles.add(general.getImageFile())) throw new AppException("数据包武将图片异常或重复");
        }
    }

    private AppSettingsRequest toSettingsRequest(AppSettingsVO settings) {
        AppSettingsRequest request = new AppSettingsRequest();
        request.setManualPickEnabled(settings.isManualPickEnabled());
        request.setCrownPrinceEnabled(settings.isCrownPrinceEnabled());
        request.setGeneralSwapEnabled(settings.isGeneralSwapEnabled());
        request.setFactions(settings.getFactions());
        return request;
    }

    private IdentityModeRequest toModeRequest(IdentityModeVO source) {
        IdentityModeRequest request = new IdentityModeRequest();
        request.setName(source.getName());
        request.setEnabled(source.getEnabled());
        request.setRules(source.getRules().stream().map(this::toRuleRequest).toList());
        return request;
    }

    private IdentityModeRuleRequest toRuleRequest(IdentityModeRuleVO source) {
        IdentityModeRuleRequest request = new IdentityModeRuleRequest();
        request.setPlayerCount(source.getPlayerCount());
        request.setIdentityName(source.getIdentityName());
        request.setQuantity(source.getQuantity());
        request.setGeneralPoolSize(source.getGeneralPoolSize());
        request.setGeneralSelectCount(source.getGeneralSelectCount());
        request.setIsLeader(source.getIsLeader());
        request.setIdentityVisible(source.getIdentityVisible());
        request.setAllowLordGeneral(source.getAllowLordGeneral());
        request.setSameIdentityGeneralVisible(source.getSameIdentityGeneralVisible());
        request.setInitialHpBonus(source.getInitialHpBonus());
        request.setMaxHpBonus(source.getMaxHpBonus());
        request.setSortOrder(source.getSortOrder());
        return request;
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private Path resolveGeneralImage(General general) {
        if (general.getImagePath() == null || !general.getImagePath().startsWith("/upload/general/")) throw new AppException("武将“" + general.getName() + "”图片路径无效，无法导出");
        String filename = general.getImagePath().substring("/upload/general/".length());
        if (!isSimpleImageFilename(filename)) throw new AppException("武将“" + general.getName() + "”图片路径无效，无法导出");
        Path folder = Path.of(uploadDir).toAbsolutePath().normalize().resolve("general");
        Path target = folder.resolve(filename).normalize();
        if (!target.startsWith(folder) || !Files.isRegularFile(target)) throw new AppException("武将“" + general.getName() + "”图片文件不存在，无法导出");
        return target;
    }

    private String safeImageFilename(General general) {
        String original = general.getImagePath().substring("/upload/general/".length());
        return general.getId() + "-" + original;
    }

    private boolean isImageEntry(String name) {
        return name != null && name.startsWith(IMAGE_PREFIX) && isSimpleImageFilename(name.substring(IMAGE_PREFIX.length()));
    }

    private boolean isSimpleImageFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.startsWith(".")) return false;
        int index = filename.lastIndexOf('.');
        return index > 0 && index < filename.length() - 1 && ALLOWED_IMAGE_EXTENSIONS.contains(filename.substring(index + 1).toLowerCase(Locale.ROOT));
    }

    private byte[] readEntry(InputStream input, long maxBytes, long totalBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (output.size() + count > maxBytes || totalBytes + output.size() + count > MAX_TOTAL_UNCOMPRESSED_BYTES) throw new AppException("数据包内容超过限制");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private long copyEntry(InputStream input, Path target, long maxBytes, long remainingBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(target)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > maxBytes || total > remainingBytes) throw new AppException("数据包图片超过限制");
                output.write(buffer, 0, count);
            }
        }
        return total;
    }

    private String generalNameKey(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private String calculateSha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AppException("当前环境不支持 SHA-256 校验");
        } catch (IOException exception) {
            throw new AppException("读取数据包失败");
        }
    }

    private boolean verifySharedPackage(String sha256, boolean allowUnverified) {
        if (sharedHallVerificationUrl == null || sharedHallVerificationUrl.isBlank()) return false;
        try {
            String separator = sharedHallVerificationUrl.contains("?") ? "&" : "?";
            URI uri = URI.create(sharedHallVerificationUrl + separator + "sha256=" + URLEncoder.encode(sha256, java.nio.charset.StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("HTTP " + response.statusCode());
            boolean verified = objectMapper.readTree(response.body()).path("data").asBoolean(false);
            if (!verified && !allowUnverified) throw new AppException("文件未匹配共享大厅的审核 SHA-256，已拒绝导入；本地备份请使用“本地备份导入”");
            return verified;
        } catch (AppException exception) {
            throw exception;
        } catch (IOException exception) {
            if (allowUnverified) return false;
            throw new AppException("无法连接共享大厅验证文件；请检查网络，或选择本地备份导入");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (allowUnverified) return false;
            throw new AppException("共享大厅验证已中断，请重试");
        } catch (RuntimeException exception) {
            if (allowUnverified) return false;
            throw new AppException("共享大厅验证地址配置无效");
        }
    }

    private String modeNameKey(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record ImportedPackage(DataPackageManifest manifest, Map<String, Path> images) { }

    @Data
    public static class DataPackageManifest {
        private String format;
        private Integer version;
        private String exportedAt;
        private AppSettingsRequest settings;
        private List<IdentityModeRequest> identityModes = new ArrayList<>();
        private List<PackageGeneral> generals = new ArrayList<>();
    }

    @Data
    public static class PackageGeneral {
        private String name;
        private String faction;
        private Boolean isLord;
        private Boolean startsHidden;
        private Integer initialHp;
        private Integer maxHp;
        private Integer initialArmor;
        private String imageFile;

        private static PackageGeneral from(General general) {
            PackageGeneral item = new PackageGeneral();
            item.setName(general.getName());
            item.setFaction(general.getFaction());
            item.setIsLord(general.getIsLord());
            item.setStartsHidden(general.getStartsHidden());
            item.setInitialHp(general.getInitialHp());
            item.setMaxHp(general.getMaxHp());
            item.setInitialArmor(general.getInitialArmor());
            return item;
        }
    }

    private static class PathMultipartFile implements MultipartFile {
        private final Path path;
        private PathMultipartFile(Path path) { this.path = path; }
        @Override public String getName() { return "image"; }
        @Override public String getOriginalFilename() { return path.getFileName().toString(); }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { try { return Files.size(path) == 0; } catch (IOException exception) { return true; } }
        @Override public long getSize() { try { return Files.size(path); } catch (IOException exception) { return 0; } }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        @Override public void transferTo(java.io.File destination) throws IOException, IllegalStateException { Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        @Override public void transferTo(Path destination) throws IOException, IllegalStateException { Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
}
