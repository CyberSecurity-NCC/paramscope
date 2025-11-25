package org.paramscope.analysis;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkResourcesPack {

    private static final Set<String> RESOURCE_DIRECTORIES = new HashSet<>(Arrays.asList(
            "res/",
            "assets/",
            "META-INF/",
            "lib/",
            "libs/"));

    private static final Set<String> TEXT_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".xml", ".json", ".properties", ".txt", ".conf", ".config",
            ".ini", ".yaml", ".yml", ".toml", ".res", ".arsc", ".so",
            ".mf", ".sf", ".rsa", ".cert", ".pem", ".key", ".keystore"));

    private static final long RESOURCE_SIZE_THRESHOLD = 1024 * 1024; // 1MB

    public static void repackResourcesToJar(String apkPath) {
        Path apkPathObj = Paths.get(apkPath);
        String apkFileName = apkPathObj.getFileName().toString();
        String baseName = apkFileName.substring(0, apkFileName.length() - 4);

        Path tmpDir = Paths.get("tmp").toAbsolutePath();
        Path jarPath = tmpDir.resolve(baseName + ".jar");

        Path apkExtractedDir = tmpDir.resolve(baseName + "_apk_extracted");
        Path jarExtractedDir = tmpDir.resolve(baseName + "_jar_extracted");

        try {
            System.out.println("Extracting APK file...");
            extractZipFile(apkPath, apkExtractedDir);

            System.out.println("Extracting JAR file...");
            extractZipFile(jarPath.toString(), jarExtractedDir);

            System.out.println("Scanning resource files...");
            List<ResourceFileInfo> resourceFiles = scanResourceFiles(apkExtractedDir, apkExtractedDir);

            System.out.println("Found " + resourceFiles.size() + " resource files to repack");

            System.out.println("Copying resource files to JAR directory...");
            for (ResourceFileInfo resourceInfo : resourceFiles) {

                Path targetPathWithDir = jarExtractedDir.resolve(resourceInfo.relativePath);
                Files.createDirectories(targetPathWithDir.getParent());
                Files.copy(resourceInfo.filePath, targetPathWithDir, StandardCopyOption.REPLACE_EXISTING);

                String flatFileName = resourceInfo.relativePath.replace("/", "_").replace("\\", "_");
                Path targetPathRoot = jarExtractedDir.resolve(flatFileName);
                Files.copy(resourceInfo.filePath, targetPathRoot, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("Repacking JAR file...");
            repackJarFile(jarExtractedDir, jarPath);

            System.out.println("Resource repacking completed successfully");

        } catch (Exception e) {
            System.err.println("Error during resource repacking: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to repack resources", e);
        } finally {

            System.out.println("Cleaning up temporary files...");
            cleanupDirectory(apkExtractedDir);
            cleanupDirectory(jarExtractedDir);
        }
    }

    private static void extractZipFile(String zipFilePath, Path extractTo) throws IOException {

        if (Files.exists(extractTo)) {
            cleanupDirectory(extractTo);
        }
        Files.createDirectories(extractTo);

        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = extractTo.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    continue;
                }

                Files.createDirectories(entryPath.getParent());

                try (InputStream is = zipFile.getInputStream(entry);
                        FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private static List<ResourceFileInfo> scanResourceFiles(Path rootDir, Path baseDir) throws IOException {
        List<ResourceFileInfo> resourceFiles = new ArrayList<>();

        Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {

                    Path relativePath = baseDir.relativize(file);
                    String relativePathStr = relativePath.toString().replace("\\", "/");

                    long fileSize = attrs.size();
                    if (fileSize >= RESOURCE_SIZE_THRESHOLD) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (isTextResourceFile(relativePathStr)) {
                        resourceFiles.add(new ResourceFileInfo(file, relativePathStr));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return resourceFiles;
    }

    private static boolean isTextResourceFile(String relativePath) {

        boolean inResourceDir = false;
        for (String dir : RESOURCE_DIRECTORIES) {
            if (relativePath.startsWith(dir)) {
                inResourceDir = true;
                break;
            }
        }

        String lowerPath = relativePath.toLowerCase();
        boolean hasTextExtension = false;
        for (String ext : TEXT_FILE_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                hasTextExtension = true;
                break;
            }
        }

        if (relativePath.equals("resources.arsc") || relativePath.equals("AndroidManifest.xml")) {
            return true;
        }

        return inResourceDir || hasTextExtension;
    }

    private static void repackJarFile(Path jarExtractedDir, Path jarPath) throws IOException, InterruptedException {

        Path tempJarPath = jarPath.resolveSibling(jarPath.getFileName().toString() + ".tmp");

        String jarCommand = "jar";
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path jarPathInJavaHome = Paths.get(javaHome).resolve("bin").resolve("jar" + (isWindows() ? ".exe" : ""));
            if (Files.exists(jarPathInJavaHome)) {
                jarCommand = jarPathInJavaHome.toString();
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
                jarCommand, "cf", tempJarPath.toAbsolutePath().toString(),
                "-C", jarExtractedDir.toAbsolutePath().toString(), ".");

        pb.directory(jarExtractedDir.getParent().toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = "jar command failed with exit code: " + exitCode;
            if (output.length() > 0) {
                errorMsg += "\nOutput: " + output.toString();
            }
            throw new IOException(errorMsg);
        }

        if (Files.exists(jarPath)) {
            Files.delete(jarPath);
        }
        Files.move(tempJarPath, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private static void cleanupDirectory(Path directory) {
        if (Files.exists(directory)) {
            try {
                Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                System.err.println("Warning: Failed to cleanup directory " + directory + ": " + e.getMessage());
            }
        }
    }

    private static class ResourceFileInfo {
        final Path filePath;
        final String relativePath;

        ResourceFileInfo(Path filePath, String relativePath) {
            this.filePath = filePath;
            this.relativePath = relativePath;
        }
    }
}
