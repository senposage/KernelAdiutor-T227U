/*
 * Copyright (C) 2015-2016 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.grarak.kerneladiutor.utils.kernel.vm;

import android.content.Context;

import com.grarak.kerneladiutor.fragments.ApplyOnBootFragment;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.root.Control;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by willi on 03.08.16.
 */
public class ZRAM {

    private static final String ZRAM = "/sys/block/zram0";
    private static final String BLOCK = "/dev/block/zram0";
    private static final String DISKSIZE = "/sys/block/zram0/disksize";
    private static final String RESET = "/sys/block/zram0/reset";
    private static final String MAX_COMP_STREAMS = "/sys/block/zram0/max_comp_streams";
    private static final String COMP_ALGORITHM = "/sys/block/zram0/comp_algorithm";
    private static final String WRITEBACK_LIMIT = "/sys/block/zram0/writeback_limit";
    private static final String WRITEBACK_LIMIT_ENABLE = "/sys/block/zram0/writeback_limit_enable";

    public static void setDisksize(final int value, final Context context) {
        String maxCompStrems = null;
        if (Utils.existFile(MAX_COMP_STREAMS)) {
            maxCompStrems = Utils.readFile(MAX_COMP_STREAMS);
        }
        int size = value * 1024 * 1024;
        run("swapoff " + BLOCK + " > /dev/null 2>&1", BLOCK + "swapoff", context);
        run(Control.write("1", RESET), RESET, context);
        run(Control.write("0", DISKSIZE), DISKSIZE + "reset", context);
        if (maxCompStrems != null) {
            run(Control.write(maxCompStrems, MAX_COMP_STREAMS), MAX_COMP_STREAMS, context);
        }
        if (size != 0) {
            run(Control.write(String.valueOf(size), DISKSIZE), DISKSIZE, context);
            run("mkswap " + BLOCK + " > /dev/null 2>&1", BLOCK + "mkswap", context);
            run("swapon " + BLOCK + " > /dev/null 2>&1", BLOCK + "swapon", context);
        }
    }

    public static int getDisksize() {
        return Utils.strToInt(Utils.readFile(DISKSIZE)) / 1024 / 1024;
    }

    /**
     * Recreating zram requires swapoff.  On a live Android system that can
     * block indefinitely while the VM tries to reclaim swapped pages, which
     * in turn holds KA's serialized root command queue.  Only permit those
     * destructive controls while zram is not an active swap device.
     */
    public static boolean isSwapActive() {
        String swaps = Utils.readFile("/proc/swaps");
        return swaps != null && swaps.contains("zram0");
    }

    public static boolean hasCompressionAlgorithm() {
        return Utils.existFile(COMP_ALGORITHM);
    }

    public static List<String> getCompressionAlgorithms() {
        List<String> algorithms = new ArrayList<>();
        String value = Utils.readFile(COMP_ALGORITHM);
        if (value == null) return algorithms;
        for (String algorithm : value.trim().split("\\s+")) {
            algorithm = algorithm.replace("[", "").replace("]", "");
            if (!algorithm.isEmpty()) algorithms.add(algorithm);
        }
        return algorithms;
    }

    public static String getCompressionAlgorithm() {
        String value = Utils.readFile(COMP_ALGORITHM);
        if (value == null) return "";
        for (String algorithm : value.trim().split("\\s+")) {
            if (algorithm.startsWith("[") && algorithm.endsWith("]")) {
                return algorithm.substring(1, algorithm.length() - 1);
            }
        }
        return "";
    }

    /** Changing the algorithm requires a zram reset; recreate the active
     * swap device with the same size and stream count. */
    public static void setCompressionAlgorithm(String algorithm, Context context) {
        int size = getDisksize();
        int streams = getMaxCompStreams();
        run("swapoff " + BLOCK + " > /dev/null 2>&1", BLOCK + "swapoff-algorithm", context);
        run(Control.write("1", RESET), RESET + "algorithm", context);
        run(Control.write(algorithm, COMP_ALGORITHM), COMP_ALGORITHM, context);
        if (streams > 0) {
            run(Control.write(String.valueOf(streams), MAX_COMP_STREAMS), MAX_COMP_STREAMS, context);
        }
        if (size > 0) {
            run(Control.write(String.valueOf(size * 1024 * 1024), DISKSIZE), DISKSIZE, context);
            run("mkswap " + BLOCK + " > /dev/null 2>&1", BLOCK + "mkswap-algorithm", context);
            run("swapon " + BLOCK + " > /dev/null 2>&1", BLOCK + "swapon-algorithm", context);
        }
    }

    public static boolean hasMaxCompStreams() {
        return Utils.existFile(MAX_COMP_STREAMS);
    }

    public static int getMaxCompStreams() {
        return Utils.strToInt(Utils.readFile(MAX_COMP_STREAMS));
    }

    public static void setMaxCompStreams(int streams, Context context) {
        Control.runSetting(Control.write(String.valueOf(streams), MAX_COMP_STREAMS),
                ApplyOnBootFragment.VM, MAX_COMP_STREAMS, context);
    }

    public static boolean hasWritebackLimit() {
        return Utils.existFile(WRITEBACK_LIMIT) && Utils.existFile(WRITEBACK_LIMIT_ENABLE);
    }

    public static boolean isWritebackLimitEnabled() {
        return Utils.strToInt(Utils.readFile(WRITEBACK_LIMIT_ENABLE)) == 1;
    }

    public static void setWritebackLimitEnabled(boolean enabled, Context context) {
        Control.runSetting(Control.write(enabled ? "1" : "0", WRITEBACK_LIMIT_ENABLE),
                ApplyOnBootFragment.VM, WRITEBACK_LIMIT_ENABLE, context);
    }

    /** Kernel interface uses 4 KiB pages. */
    public static int getWritebackLimitPages() {
        return Utils.strToInt(Utils.readFile(WRITEBACK_LIMIT));
    }

    public static void setWritebackLimitPages(int pages, Context context) {
        Control.runSetting(Control.write(String.valueOf(pages), WRITEBACK_LIMIT),
                ApplyOnBootFragment.VM, WRITEBACK_LIMIT, context);
    }

    public static boolean supported() {
        return Utils.existFile(ZRAM);
    }

    private static void run(String command, String id, Context context) {
        Control.runSetting(command, ApplyOnBootFragment.VM, id, context);
    }

}
