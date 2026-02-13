package com.mkpro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MakerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testBackItUp() throws IOException, NoSuchAlgorithmException {
        // Create a temporary file
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.writeString(testFile, content);

        // Expected MD5
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(content.getBytes());
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String expectedMd5 = sb.toString();

        // Call backItUp
        String actualMd5 = Maker.backItUp(testFile.toFile());

        // Assert MD5
        assertEquals(expectedMd5, actualMd5, "MD5 checksum should match");

        // Assert backup file existence
        String userHome = System.getProperty("user.home");
        Path backupPath = Paths.get(userHome, ".mkpro", "backups", expectedMd5, "test.txt");
        assertTrue(Files.exists(backupPath), "Backup file should exist at: " + backupPath);

        // Assert content matches
        String backupContent = Files.readString(backupPath);
        assertEquals(content, backupContent, "Backup content should match original content");
    }
}
