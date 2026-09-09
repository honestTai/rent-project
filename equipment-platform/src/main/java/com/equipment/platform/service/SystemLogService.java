package com.equipment.platform.service;

import com.equipment.platform.dto.SystemLogQuery;
import com.equipment.platform.vo.SystemLogFileVo;
import com.equipment.platform.vo.SystemLogLineVo;
import com.equipment.platform.vo.SystemLogSourceVo;
import com.equipment.platform.vo.SystemLogViewVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 系统级 Docker 日志读取服务。
 * <p>
 * 只读取部署时挂载到中台容器的日志目录，文件列表由服务端枚举生成，支持读取当前日志和 logback 归档日志。
 * </p>
 */
@Service
public class SystemLogService {

    private static final int DEFAULT_TAIL = 300;
    private static final int MAX_TAIL = 1000;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final int MAX_ARCHIVE_DEPTH = 5;
    private static final int MAX_FILES_PER_SOURCE = 300;
    private static final long MAX_READ_BYTES = 4L * 1024L * 1024L;
    private static final List<String> LEVELS = Arrays.asList("error", "warn", "info", "access");
    private static final Map<String, SourceDef> SOURCES = buildSources();
    private static final String LOG_ROOT_CONFIG = "system-log.root-path";
    private static final String LOG_ROOT_ENV = "SYSTEM_LOG_ROOT_PATH";
    private static final String DOCKER_LOG_ROOT = "/app/service-logs";
    private static final String LOCAL_LOG_ROOT = "logs";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PlatformConfigService configService;

    /**
     * 创建系统级日志读取服务。
     *
     * @param configService 中台配置服务
     */
    public SystemLogService(PlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 查询可用日志来源和文件。
     *
     * @return 日志来源列表
     */
    public List<SystemLogSourceVo> listSources() {
        List<SystemLogSourceVo> rows = new ArrayList<>();
        for (Map.Entry<String, SourceDef> entry : SOURCES.entrySet()) {
            List<SystemLogFileVo> files = listLogFiles(entry.getValue());
            rows.add(new SystemLogSourceVo(
                    entry.getKey(),
                    entry.getValue().getName(),
                    entry.getValue().getDirectory(),
                    existingLevels(files),
                    files
            ));
        }
        return rows;
    }

    /**
     * 返回系统日志支持的系统编码。
     *
     * @return 系统编码集合
     */
    public Set<String> supportedSystemCodes() {
        return new LinkedHashSet<>(SOURCES.keySet());
    }

    /**
     * 查询当前或归档日志文件。
     *
     * @param query 查询条件
     * @return 日志查询结果
     */
    public SystemLogViewVo query(SystemLogQuery query) {
        SystemLogQuery safeQuery = query == null ? new SystemLogQuery() : query;
        String systemCode = normalizeSystemCode(safeQuery.getSystemCode());
        int tail = normalizeTail(safeQuery.getTail());
        String keyword = StringUtils.hasText(safeQuery.getKeyword()) ? safeQuery.getKeyword().trim() : null;

        SourceDef source = SOURCES.get(systemCode);
        FileSelection selection = StringUtils.hasText(safeQuery.getFileKey())
                ? resolveByFileKey(resolveLogRoot(), source, safeQuery.getFileKey())
                : resolveCurrentFile(resolveLogRoot(), source, normalizeLevel(safeQuery.getLevel()));

        SystemLogViewVo result = new SystemLogViewVo();
        result.setSystemCode(systemCode);
        result.setLevel(selection.level);
        result.setFileKey(selection.fileKey);
        result.setFileName(selection.fileName);
        result.setArchive(selection.archive);
        result.setFilePath(selection.file.getPath());
        result.setExists(selection.file.isFile());
        result.setLines(selection.file.isFile() ? toLineVos(readTail(selection.file, tail, keyword), selection.level) : Collections.emptyList());
        return result;
    }

    private List<SystemLogLineVo> toLineVos(List<String> lines, String defaultLevel) {
        List<SystemLogLineVo> rows = new ArrayList<>();
        int index = 1;
        for (String line : lines) {
            rows.add(new SystemLogLineVo(index++, maskSensitive(line), inferLevel(line, defaultLevel)));
        }
        return rows;
    }

    private List<String> readTail(File file, int tail, String keyword) {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length <= 0) {
                return lines;
            }
            long start = Math.max(0L, length - MAX_READ_BYTES);
            raf.seek(start);
            byte[] buffer = new byte[(int) Math.min(READ_BUFFER_SIZE, length - start)];
            StringBuilder content = new StringBuilder();
            int len;
            while ((len = raf.read(buffer)) != -1) {
                content.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            String[] allLines = content.toString().split("\\r?\\n");
            for (String line : allLines) {
                addMatchedLine(lines, line, keyword);
            }
            if (lines.size() > tail) {
                return new ArrayList<>(lines.subList(lines.size() - tail, lines.size()));
            }
        } catch (IOException e) {
            lines.add("读取日志失败: " + e.getMessage());
        }
        return lines;
    }

    private void addMatchedLine(List<String> lines, String line, String keyword) {
        if (!StringUtils.hasText(keyword) || line.contains(keyword)) {
            lines.add(line);
        }
    }

    private List<SystemLogFileVo> listLogFiles(SourceDef source) {
        File root = resolveLogRoot();
        List<File> files = new ArrayList<>();
        for (String directoryName : source.getDirectories()) {
            File sourceDir = new File(root, directoryName);
            if (!sourceDir.isDirectory()) {
                continue;
            }
            collectCurrentFiles(sourceDir, files);
            collectArchivedFiles(sourceDir, files, 0);
        }

        files.sort(Comparator
                .comparing((File file) -> isCurrentFile(file) ? 0 : 1)
                .thenComparing(File::lastModified, Comparator.reverseOrder())
                .thenComparing(File::getPath));

        List<SystemLogFileVo> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (File file : files) {
            if (!file.isFile() || rows.size() >= MAX_FILES_PER_SOURCE) {
                continue;
            }
            SystemLogFileVo vo = toFileVo(root, file);
            if (seen.add(vo.getFileKey())) {
                rows.add(vo);
            }
        }
        return rows;
    }

    private void collectCurrentFiles(File sourceDir, List<File> files) {
        for (String level : LEVELS) {
            File file = new File(sourceDir, level + ".log");
            if (file.isFile()) {
                files.add(file);
            }
        }
    }

    private void collectArchivedFiles(File dir, List<File> files, int depth) {
        if (depth > MAX_ARCHIVE_DEPTH || !dir.isDirectory() || files.size() >= MAX_FILES_PER_SOURCE * 2) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectArchivedFiles(child, files, depth + 1);
            } else if (isLogFile(child) && !isCurrentFile(child)) {
                files.add(child);
            }
        }
    }

    private List<String> existingLevels(List<SystemLogFileVo> files) {
        Set<String> levels = new LinkedHashSet<>();
        for (SystemLogFileVo file : files) {
            if (StringUtils.hasText(file.getLevel())) {
                levels.add(file.getLevel());
            }
        }
        return new ArrayList<>(levels);
    }

    private FileSelection resolveCurrentFile(File root, SourceDef source, String level) {
        File sourceDir = resolveSourceDirectory(root, source);
        File file = new File(sourceDir, level + ".log");
        return toSelection(root, file, level, false);
    }

    private FileSelection resolveByFileKey(File root, SourceDef source, String fileKey) {
        String normalized = normalizeFileKey(fileKey);
        File file = new File(root, normalized);
        ensureAllowedLogFile(root, source, file);
        return toSelection(root, file, inferLevel(file), !isCurrentFile(file));
    }

    private FileSelection toSelection(File root, File file, String level, boolean archive) {
        String fileKey = relativePath(root, file);
        return new FileSelection(file, fileKey, displayFileName(file, fileKey, level, archive), level, archive);
    }

    private SystemLogFileVo toFileVo(File root, File file) {
        String fileKey = relativePath(root, file);
        String level = inferLevel(file);
        boolean archive = !isCurrentFile(file);
        return new SystemLogFileVo(
                fileKey,
                displayFileName(file, fileKey, level, archive),
                level,
                archive,
                Math.max(0L, file.length()),
                DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(file.lastModified()))
        );
    }

    private String displayFileName(File file, String fileKey, String level, boolean archive) {
        String upperLevel = levelText(level);
        if (!archive) {
            return "当前 " + upperLevel + "（" + file.getName() + "）";
        }
        return "归档 " + upperLevel + "（" + fileKey + "）";
    }

    private String levelText(String level) {
        if ("error".equals(level)) {
            return "ERROR";
        }
        if ("warn".equals(level)) {
            return "WARN";
        }
        if ("info".equals(level)) {
            return "INFO";
        }
        if ("access".equals(level)) {
            return "ACCESS";
        }
        return StringUtils.hasText(level) ? level.toUpperCase(Locale.ROOT) : "LOG";
    }

    private File resolveLogRoot() {
        String envRoot = System.getenv(LOG_ROOT_ENV);
        if (StringUtils.hasText(envRoot)) {
            return new File(envRoot.trim());
        }
        String configured = configService.getRuntimeValue("platform", LOG_ROOT_CONFIG);
        if (StringUtils.hasText(configured)) {
            return new File(configured.trim());
        }
        File dockerRoot = new File(DOCKER_LOG_ROOT);
        if (dockerRoot.isDirectory()) {
            return dockerRoot;
        }
        return new File(LOCAL_LOG_ROOT);
    }

    private File resolveSourceDirectory(File root, SourceDef source) {
        for (String directoryName : source.getDirectories()) {
            File candidate = new File(root, directoryName);
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        return new File(root, source.getDirectory());
    }

    private void ensureAllowedLogFile(File root, SourceDef source, File file) {
        try {
            String filePath = file.getCanonicalPath();
            for (String directoryName : source.getDirectories()) {
                File sourceDir = new File(root, directoryName);
                String sourcePath = sourceDir.getCanonicalPath();
                if (filePath.equals(sourcePath) || filePath.startsWith(sourcePath + File.separator)) {
                    if (!isLogFile(file)) {
                        throw new IllegalArgumentException("不支持的日志文件类型");
                    }
                    return;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("日志文件路径无效");
        }
        throw new IllegalArgumentException("日志文件不属于当前系统");
    }

    private String normalizeFileKey(String fileKey) {
        String normalized = fileKey == null ? "" : fileKey.trim().replace('\\', '/');
        if (!StringUtils.hasText(normalized)
                || normalized.startsWith("/")
                || normalized.contains("../")
                || normalized.contains("..\\")
                || normalized.equals("..")) {
            throw new IllegalArgumentException("日志文件标识无效");
        }
        return normalized;
    }

    private String relativePath(File root, File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootPath + File.separator)) {
                return filePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
            }
        } catch (IOException ignored) {
            // 使用普通路径作为展示值，仍然不参与权限判断。
        }
        return file.getPath().replace(File.separatorChar, '/');
    }

    private boolean isLogFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".log");
    }

    private boolean isCurrentFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return LEVELS.contains(name.replace(".log", "")) && file.getParentFile() != null;
    }

    private String normalizeSystemCode(String systemCode) {
        String normalized = StringUtils.hasText(systemCode) ? systemCode.trim().toLowerCase(Locale.ROOT) : "platform";
        if (!SOURCES.containsKey(normalized)) {
            throw new IllegalArgumentException("不支持的系统日志来源: " + systemCode);
        }
        return normalized;
    }

    private String normalizeLevel(String level) {
        String normalized = StringUtils.hasText(level) ? level.trim().toLowerCase(Locale.ROOT) : "error";
        if (!LEVELS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的日志级别: " + level);
        }
        return normalized;
    }

    private int normalizeTail(Integer tail) {
        if (tail == null || tail <= 0) {
            return DEFAULT_TAIL;
        }
        return Math.min(tail, MAX_TAIL);
    }

    private String inferLevel(File file) {
        String path = file.getPath().toLowerCase(Locale.ROOT).replace('\\', '/');
        for (String level : LEVELS) {
            if (path.endsWith("/" + level + ".log")
                    || path.contains("/" + level + "/")
                    || path.contains("/" + level + ".")) {
                return level;
            }
        }
        return "info";
    }

    private String inferLevel(String line, String defaultLevel) {
        if (line != null) {
            if (line.contains(" ERROR ")) {
                return "error";
            }
            if (line.contains(" WARN ")) {
                return "warn";
            }
            if (line.contains(" INFO ")) {
                return "info";
            }
        }
        return defaultLevel;
    }

    private String maskSensitive(String line) {
        if (line == null) {
            return "";
        }
        return line
                .replaceAll("(?i)(password|userPwd|token|access[_-]?key|secret|appSecret)(\\s*[=:]\\s*)[^,\\s}]+", "$1$2******")
                .replaceAll("(?i)(Authorization:\\s*Bearer\\s+)[^,\\s}]+", "$1******");
    }

    private static Map<String, SourceDef> buildSources() {
        Map<String, SourceDef> sources = new LinkedHashMap<>();
        sources.put("platform", new SourceDef("中台服务", "platform", "equipment-platform"));
        sources.put("gateway", new SourceDef("网关服务", "gateway", "equipment-gateway"));
        sources.put("alipay", new SourceDef("支付宝租赁", "alipay", "equipment-alipay"));
        sources.put("eureka", new SourceDef("注册中心", "eureka", "equipment-eureka"));
        sources.put("nginx", new SourceDef("Nginx 网关", "nginx", "nginx"));
        return sources;
    }

    /**
     * 固定日志来源定义。
     */
    private static class SourceDef {
        private final String name;
        private final String directory;
        private final String legacyDirectory;

        private SourceDef(String name, String directory, String legacyDirectory) {
            this.name = name;
            this.directory = directory;
            this.legacyDirectory = legacyDirectory;
        }

        private String getName() {
            return name;
        }

        private String getDirectory() {
            return directory;
        }

        private List<String> getDirectories() {
            if (directory.equals(legacyDirectory)) {
                return Collections.singletonList(directory);
            }
            return Arrays.asList(directory, legacyDirectory);
        }
    }

    private static class FileSelection {
        private final File file;
        private final String fileKey;
        private final String fileName;
        private final String level;
        private final boolean archive;

        private FileSelection(File file, String fileKey, String fileName, String level, boolean archive) {
            this.file = file;
            this.fileKey = fileKey;
            this.fileName = fileName;
            this.level = level;
            this.archive = archive;
        }
    }
}
