package com.common.Util.FileClip;

import com.common.Entity.ReturnResult;

import java.io.File;
import java.net.URI;

import static com.common.Constant.constant.*;

/**
 * 文件夹工具类
 */
public class FileClip {

    //创建文件夹
    public static void fileClipCreat(String deviceName) {//主程序，程序入口
        File file = new File(FILE_PATH + deviceName);
        if (!file.exists()) {//如果文件夹不存在
            file.mkdir();//创建文件夹
        }
    }

    //删除实体文件(图片and视频)
    public static void deleteFileUrl(String fileUrl) {
        String path = normalizeStaticPath(fileUrl);
        //删除文件
        try {
            File file = new File(FILE_PATH + path);
            if (file.delete()) {
                System.out.println(file.getName() + " 文件已被删除！");
            } else {
                new ReturnResult(FAIL, "附件删除失败", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String normalizeStaticPath(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return "";
        }
        String path = fileUrl.trim();
        try {
            URI uri = URI.create(path);
            if (uri.isAbsolute() && uri.getPath() != null) {
                path = uri.getPath();
            }
        } catch (IllegalArgumentException ignored) {
            // 非标准 URL 按相对路径处理。
        }
        path = path.replace("\\", "/");
        if (path.startsWith("/uploads/")) {
            path = path.substring("/uploads/".length());
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    //删除二维码
    public static boolean deleteQC(String deviceOneCode, String deviceName) {
        //删除文件
        File dirFile = new File(FILE_PATH + deviceOneCode);
        if (!dirFile.exists()) {
            return false;
        }
        if (dirFile.isFile()) {
            return dirFile.delete();
        } else {
            for (File file : dirFile.listFiles()) {
                deleteFile(file.getPath().replaceAll("\\\\", "/"));
            }
        }
        return dirFile.delete();
    }

    public static void deleteFile(String fileUrl) {
        //删除文件
        try {
            File file = new File(fileUrl);
            if (file.delete()) {
                System.out.println(file.getName() + " 文件已被删除！");
            } else {
                new ReturnResult(FAIL, "附件删除失败", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
