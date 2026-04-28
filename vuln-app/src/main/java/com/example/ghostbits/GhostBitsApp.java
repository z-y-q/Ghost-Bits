package com.example.ghostbits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@RestController
public class GhostBitsApp {

    private static final Path APP_ROOT = Paths.get("").toAbsolutePath();

    public static void main(String[] args) {
        SpringApplication.run(GhostBitsApp.class, args);
    }

    @GetMapping("/")
    public String index() {
        return "<h1>Ghost Bits Path Traversal Lab</h1>" +
                "<h3>Black Hat Asia 2026 &mdash; Cast Attack Reproduction</h3>" +
                "<pre>" +
                "靶文件: sensitive.txt (在应用根目录, 需 ../ 穿越)\n" +
                "\n" +
                "1. 正常读取 (不需要穿越):\n" +
                "   curl 'http://localhost:8080/files?path=test.txt'\n" +
                "\n" +
                "2. 直接穿越 (被 WAF 拦截):\n" +
                "   curl 'http://localhost:8080/files?path=../sensitive.txt'\n" +
                "\n" +
                "3. Ghost Bits 绕过 WAF:\n" +
                "   a. 用工具变形: ../sensitive.txt\n" +
                "   b. 选择「全量变形」+ CJK 混淆\n" +
                "   c. 复制 URL 编码结果\n" +
                "   d. curl 'http://localhost:8080/files?path=&lt;URL编码结果&gt;'\n" +
                "\n" +
                "原理:\n" +
                "  WAF 看到 Unicode 字符串 (如 尮儮灼...)\n" +
                "  不包含 ASCII \"..\" , 放行\n" +
                "  (byte)char 截断高8位 → 还原 \"../\"\n" +
                "</pre>";
    }

    @GetMapping("/files")
    public String readFile(@RequestParam("path") String rawPath) {
        // ========== 简易 WAF ==========
        if (rawPath.contains("..") ||
            rawPath.toLowerCase().contains("/etc") ||
            rawPath.toLowerCase().contains("passwd") ||
            rawPath.toLowerCase().contains("shadow")) {
            return "[BLOCKED] WAF: Path traversal detected in request string: " + rawPath;
        }

        // ========== Ghost Bits 还原 ==========
        // 模拟 Java 框架中 char → byte 截断高 8 位的还原过程
        byte[] pathBytes = new byte[rawPath.length()];
        for (int i = 0; i < rawPath.length(); i++) {
            pathBytes[i] = (byte) rawPath.charAt(i);
        }
        String restoredPath = new String(pathBytes);

        // ========== 文件读取 ==========
        // 从 files/ 子目录读取, 敏感文件在上一级
        Path baseDir = APP_ROOT.resolve("files");
        Path targetPath;
        try {
            targetPath = baseDir.resolve(restoredPath).normalize();
        } catch (InvalidPathException e) {
            return "[ERROR] Invalid path: " + restoredPath;
        }

        // 边界检查: 允许穿越到应用根目录, 但不能超出
        if (!targetPath.startsWith(APP_ROOT)) {
            return "[BLOCKED] Path escapes application root: " + targetPath;
        }

        try {
            String content = new String(Files.readAllBytes(targetPath));
            return "[OK] File: " + targetPath + "\n\n" + content;
        } catch (IOException e) {
            return "[ERROR] File not found: " + targetPath;
        }
    }
}
