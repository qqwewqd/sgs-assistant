package com.sanguosha.assistant.service;

import com.sanguosha.assistant.vo.DataPackageImportResult;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.web.multipart.MultipartFile;

public interface DataPackageService {
    void exportPackage(OutputStream outputStream) throws IOException;

    DataPackageImportResult importPackage(MultipartFile file, boolean allowUnverified);
}
