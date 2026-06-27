package com.sanguosha.assistant.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanguosha.assistant.entity.General;
import com.sanguosha.assistant.mapper.GeneralMapper;
import com.sanguosha.assistant.service.GeneralService;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.GeneralVO;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.ResultCode;
import com.sanguosha.assistant.vo.RoomView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class GeneralServiceImpl implements GeneralService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String UPLOAD_PREFIX = "/upload/";
    private static final String GENERAL_UPLOAD_PREFIX = "/upload/general/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final GeneralMapper generalMapper;

    @Value("${sgs.upload.dir}")
    private String uploadDir;

    @Override
    public PageVO<GeneralVO> listGenerals(String keyword, Boolean lordOnly, Integer page, Integer pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        LambdaQueryWrapper<General> wrapper = new LambdaQueryWrapper<General>()
                .and(normalizedKeyword != null && !normalizedKeyword.isBlank(), condition -> condition
                        .like(General::getName, normalizedKeyword)
                        .or()
                .like(General::getImagePath, normalizedKeyword))
                .eq(Boolean.TRUE.equals(lordOnly), General::getIsLord, true)
                .orderByAsc(General::getId);
        Page<General> result = generalMapper.selectPage(
                new Page<>(normalizePage(page), normalizePageSize(pageSize)),
                wrapper
        );
        return PageVO.of(
                result.getRecords().stream().map(GeneralVO::from).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    public List<RoomView.GeneralCard> listLordCards(String keyword) {
        LambdaQueryWrapper<General> wrapper = new LambdaQueryWrapper<General>()
                .eq(General::getIsLord, true)
                .like(keyword != null && !keyword.trim().isBlank(), General::getName, keyword.trim())
                .orderByAsc(General::getName);
        return generalMapper.selectList(wrapper).stream().map(this::toCard).toList();
    }

    @Override
    @Transactional
    public GeneralVO createGeneral(String name, String faction, Boolean isLord, Boolean startsHidden, String imageName, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AppException("请上传武将图片");
        }
        String normalizedName = normalizeName(name);

        General general = new General();
        general.setName(normalizedName);
        general.setFaction(normalizeFaction(faction));
        general.setImagePath(storeImage(image, imageName, null));
        general.setIsLord(Boolean.TRUE.equals(isLord));
        general.setStartsHidden(Boolean.TRUE.equals(startsHidden));
        general.setCreatedAt(LocalDateTime.now());
        general.setUpdatedAt(LocalDateTime.now());
        generalMapper.insert(general);
        return GeneralVO.from(general);
    }

    @Override
    @Transactional
    public GeneralVO updateGeneral(Long id, String name, String faction, Boolean isLord, Boolean startsHidden, String imageName, MultipartFile image) {
        General general = requireGeneral(id);
        if (name != null && !name.trim().isBlank()) {
            String normalizedName = normalizeName(name);
            general.setName(normalizedName);
        }
        if (faction != null) {
            general.setFaction(normalizeFaction(faction));
        }
        if (isLord != null) {
            general.setIsLord(isLord);
        }
        if (startsHidden != null) {
            general.setStartsHidden(startsHidden);
        }
        if (image != null && !image.isEmpty()) {
            String oldPath = general.getImagePath();
            String newPath = storeImage(image, imageName, oldPath);
            general.setImagePath(newPath);
            if (!newPath.equals(oldPath)) {
                deleteStoredImage(oldPath);
            }
        } else if (imageName != null && !imageName.trim().isBlank()) {
            general.setImagePath(renameStoredImage(general.getImagePath(), imageName));
        }
        general.setUpdatedAt(LocalDateTime.now());
        generalMapper.updateById(general);
        return GeneralVO.from(general);
    }

    @Override
    @Transactional
    public void deleteGeneral(Long id) {
        General general = requireGeneral(id);
        generalMapper.deleteById(id);
        deleteStoredImage(general.getImagePath());
    }

    @Override
    public General requireGeneral(Long id) {
        if (id == null) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        General general = generalMapper.selectById(id);
        if (general == null) {
            throw new AppException("武将不存在");
        }
        return general;
    }

    @Override
    public List<General> allGenerals() {
        return generalMapper.selectList(new LambdaQueryWrapper<General>().orderByAsc(General::getId));
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        return name.trim();
    }

    private String normalizeFaction(String faction) {
        if (faction == null || faction.trim().isBlank()) {
            return null;
        }
        return faction.trim().toUpperCase(Locale.ROOT);
    }

    private long normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String storeImage(MultipartFile image, String imageName, String replaceableImagePath) {
        String extension = extensionOf(image.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException("图片仅支持 jpg、jpeg、png、webp");
        }
        Path folder = generalUploadFolder();
        try {
            Files.createDirectories(folder);
            Path replaceable = storedImagePath(replaceableImagePath);
            String filename = resolveStoredImageFilename(imageName, extension, replaceable);
            Path target = folder.resolve(filename).normalize();
            if (!target.startsWith(folder)) {
                throw new AppException(ResultCode.VALIDATE_FAILED);
            }
            if (Files.exists(target) && (replaceable == null || !target.equals(replaceable))) {
                throw new AppException("图片文件名已存在，请重试");
            }
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return GENERAL_UPLOAD_PREFIX + filename;
        } catch (IOException ex) {
            throw new AppException("图片保存失败");
        }
    }

    private String renameStoredImage(String imagePath, String imageName) {
        Path current = storedImagePath(imagePath);
        if (current == null || !Files.exists(current)) {
            throw new AppException("原图片文件不存在");
        }
        String currentExtension = extensionOf(current.getFileName().toString());
        String filename = resolveStoredImageFilename(imageName, currentExtension, current);
        Path target = generalUploadFolder().resolve(filename).normalize();
        if (!target.startsWith(generalUploadFolder())) {
            throw new AppException(ResultCode.VALIDATE_FAILED);
        }
        if (target.equals(current)) {
            return imagePath;
        }
        if (Files.exists(target)) {
            throw new AppException("图片文件名已存在");
        }
        try {
            Files.move(current, target);
            return GENERAL_UPLOAD_PREFIX + filename;
        } catch (IOException ex) {
            throw new AppException("图片重命名失败");
        }
    }

    private String normalizeImageFilename(String imageName, String expectedExtension) {
        if (imageName == null || imageName.trim().isBlank()) {
            return "";
        }
        String filename = imageName.trim();
        if (filename.startsWith(GENERAL_UPLOAD_PREFIX)) {
            filename = filename.substring(GENERAL_UPLOAD_PREFIX.length());
        }
        if (filename.length() > 120
                || filename.equals(".")
                || filename.equals("..")
                || filename.startsWith(".")
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("?")
                || filename.contains("#")
                || filename.contains(":")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new AppException("图片文件名不合法");
        }
        String extension = extensionOf(filename);
        if (extension.isBlank()) {
            filename = filename + "." + expectedExtension;
        } else if (!compatibleExtension(extension, expectedExtension)) {
            throw new AppException("图片文件名扩展名需与图片格式一致");
        }
        return filename;
    }

    private String resolveStoredImageFilename(String imageName, String expectedExtension, Path replaceable) {
        String requested = normalizeImageFilename(imageName, expectedExtension);
        if (requested.isBlank()) {
            return UUID.randomUUID() + "." + expectedExtension;
        }
        if (replaceable != null && requested.equals(replaceable.getFileName().toString())) {
            return requested;
        }
        String extension = extensionOf(requested);
        String stem = filenameStem(requested);
        String baseName = stripTrailingIndex(stem);
        if (baseName.isBlank()) {
            throw new AppException("图片文件名不合法");
        }
        return nextAvailableIndexedFilename(baseName, extension, replaceable);
    }

    private String nextAvailableIndexedFilename(String baseName, String extension, Path replaceable) {
        for (int index = 1; index < 100000; index++) {
            String filename = baseName + "_" + index + "." + extension;
            if (filename.length() > 120) {
                throw new AppException("图片文件名过长");
            }
            if (!indexedFilenameExists(baseName, index, replaceable)) {
                return filename;
            }
        }
        throw new AppException("图片文件名编号已用尽");
    }

    private boolean indexedFilenameExists(String baseName, int index, Path replaceable) {
        Path folder = generalUploadFolder();
        for (String extension : ALLOWED_EXTENSIONS) {
            Path candidate = folder.resolve(baseName + "_" + index + "." + extension).normalize();
            if (Files.exists(candidate) && (replaceable == null || !candidate.equals(replaceable))) {
                return true;
            }
        }
        return false;
    }

    private String filenameStem(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? filename : filename.substring(0, index);
    }

    private String stripTrailingIndex(String stem) {
        int index = stem.lastIndexOf('_');
        if (index < 0 || index == stem.length() - 1) {
            return stem;
        }
        String suffix = stem.substring(index + 1);
        return suffix.chars().allMatch(Character::isDigit) ? stem.substring(0, index) : stem;
    }

    private boolean compatibleExtension(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        return Set.of("jpg", "jpeg").contains(left) && Set.of("jpg", "jpeg").contains(right);
    }

    private String extensionOf(String filename) {
        String safe = filename == null ? "" : filename;
        int index = safe.lastIndexOf('.');
        if (index < 0 || index == safe.length() - 1) {
            return "";
        }
        return safe.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteStoredImage(String imagePath) {
        Path target = storedImagePath(imagePath);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private Path storedImagePath(String imagePath) {
        if (imagePath == null || !imagePath.startsWith(GENERAL_UPLOAD_PREFIX)) {
            return null;
        }
        Path target = uploadRoot().resolve(imagePath.substring(UPLOAD_PREFIX.length())).normalize();
        if (!target.startsWith(generalUploadFolder())) {
            return null;
        }
        return target;
    }

    private Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private Path generalUploadFolder() {
        return uploadRoot().resolve("general").normalize();
    }

    private RoomView.GeneralCard toCard(General general) {
        RoomView.GeneralCard card = new RoomView.GeneralCard();
        card.setId(general.getId());
        card.setName(general.getName());
        card.setImagePath(general.getImagePath());
        card.setFaction(general.getFaction());
        card.setIsLord(general.getIsLord());
        card.setStartsHidden(general.getStartsHidden());
        return card;
    }
}
