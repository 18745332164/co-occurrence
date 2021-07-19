package com.fsck.k9.helper;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import timber.log.Timber;

import org.apache.commons.io.IOUtils;


public class FileHelper {
    public static void touchFile(final File parentDir, final String name) {
        final File file = new File(parentDir, name);
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    Timber.d("Unable to create file: %s", file.getAbsolutePath());
                }
            } else {
                if (!file.setLastModified(System.currentTimeMillis())) {
                    Timber.d("Unable to change last modification date: %s", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Timber.d(e, "Unable to touch file: %s", file.getAbsolutePath());
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            out.close();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public static void renameOrMoveByCopying(File from, File to) throws IOException {
        deleteFileIfExists(to);

        boolean renameFailed = !from.renameTo(to);
        if (renameFailed) {
            copyFile(from, to);

            boolean deleteFromFailed = !from.delete();
            if (deleteFromFailed) {
                Timber.e("Unable to delete source file after copying to destination!");
            }
        }
    }

    private static void deleteFileIfExists(File to) throws IOException {
        boolean fileDoesNotExist = !to.exists();
        if (fileDoesNotExist) {
            return;
        }

        boolean deleteOk = to.delete();
        if (deleteOk) {
            return;
        }

        throw new IOException("Unable to delete file: " + to.getAbsolutePath());
    }

    public static boolean move(final File from, final File to) {
        if (to.exists()) {
            if (!to.delete()) {
                Timber.d("Unable to delete file: %s", to.getAbsolutePath());
            }
        }

        if (!to.getParentFile().mkdirs()) {
            Timber.d("Unable to make directories: %s", to.getParentFile().getAbsolutePath());
        }

        try {
            copyFile(from, to);

            boolean deleteFromFailed = !from.delete();
            if (deleteFromFailed) {
                Timber.e("Unable to delete source file after copying to destination!");
            }
            return true;
        } catch (Exception e) {
            Timber.w(e, "cannot move %s to %s", from.getAbsolutePath(), to.getAbsolutePath());
            return false;
        }
    }

    public static void moveRecursive(final File fromDir, final File toDir) {
        if (!fromDir.exists()) {
            return;
        }
        if (!fromDir.isDirectory()) {
            if (toDir.exists()) {
                if (!toDir.delete()) {
                    Timber.w("cannot delete already existing file/directory %s", toDir.getAbsolutePath());
                }
            }
            if (!fromDir.renameTo(toDir)) {
                Timber.w("cannot rename %s to %s - moving instead", fromDir.getAbsolutePath(), toDir.getAbsolutePath());
                move(fromDir, toDir);
            }
            return;
        }
        if (!toDir.exists() || !toDir.isDirectory()) {
            if (toDir.exists()) {
                if (!toDir.delete()) {
                    Timber.d("Unable to delete file: %s", toDir.getAbsolutePath());
                }
            }
            if (!toDir.mkdirs()) {
                Timber.w("cannot create directory %s", toDir.getAbsolutePath());
            }
        }
        File[] files = fromDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                moveRecursive(file, new File(toDir, file.getName()));
                if (!file.delete()) {
                    Timber.d("Unable to delete file: %s", toDir.getAbsolutePath());
                }
            } else {
                File target = new File(toDir, file.getName());
                if (!file.renameTo(target)) {
                    Timber.w("cannot rename %s to %s - moving instead",
                            file.getAbsolutePath(), target.getAbsolutePath());
                    move(file, target);
                }
            }
        }
        if (!fromDir.delete()) {
            Timber.w("cannot delete %s", fromDir.getAbsolutePath());
        }
    }
}
