/*
 * Copyright (C) 2026
 *
 * This file is part of Kernel Adiutor.
 */
package com.grarak.kerneladiutor.utils.kernel.cpu;

import android.content.Context;

import com.grarak.kerneladiutor.fragments.ApplyOnBootFragment;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.root.Control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Controls for the C65T LL OC interface added by the T227U kernel. */
public final class T227UDVFS {

    private static final String DIRECTORY = "/proc/cpufreq/MT_CPU_DVFS_LL/";
    private static final String OPP_INDEX = DIRECTORY + "cpufreq_oppidx";
    private static final String OC_MAX_FREQ = DIRECTORY + "cpufreq_oc_max_freq";
    private static final String VOLTAGE = DIRECTORY + "cpufreq_volt";
    private static final Pattern OPP_FREQUENCY = Pattern.compile("\\(\\s*(\\d+)\\s*,");
    private static final Pattern VPROC = Pattern.compile("Vproc:\\s*(\\d+)\\s*uV");
    private static final Pattern VSRAM = Pattern.compile("Vsram:\\s*(\\d+)\\s*uV");

    public static final class RailReadback {
        private final int mVprocMillivolts;
        private final int mVsramMillivolts;

        private RailReadback(int vprocMicrovolts, int vsramMicrovolts) {
            mVprocMillivolts = vprocMicrovolts / 1000;
            mVsramMillivolts = vsramMicrovolts / 1000;
        }

        public int getVprocMillivolts() {
            return mVprocMillivolts;
        }

        public int getVsramMillivolts() {
            return mVsramMillivolts;
        }
    }

    private T227UDVFS() {
    }

    /** The explicit OC control must exist; the ordinary MTK proc files alone
     * are not sufficient because they can force DVFS rather than set a safe ceiling. */
    public static boolean supported() {
        return Utils.existFile(OPP_INDEX) && Utils.existFile(OC_MAX_FREQ)
                && Utils.existFile(VOLTAGE);
    }

    public static List<Integer> getFrequencies() {
        List<Integer> frequencies = new ArrayList<>();
        String table = Utils.readFile(OPP_INDEX);
        if (table == null) return frequencies;

        Matcher matcher = OPP_FREQUENCY.matcher(table);
        while (matcher.find()) {
            int frequency = Utils.strToInt(matcher.group(1));
            if (frequency > 0 && !frequencies.contains(frequency)) {
                frequencies.add(frequency);
            }
        }
        Collections.sort(frequencies);
        return frequencies;
    }

    public static int getMaximumFrequency() {
        return Utils.strToInt(Utils.readFile(OC_MAX_FREQ));
    }

    /** A ceiling only. PPM thermal and power restrictions remain authoritative. */
    public static void setMaximumFrequency(int frequency, Context context) {
        Control.runSetting(Control.write(String.valueOf(frequency), OC_MAX_FREQ),
                ApplyOnBootFragment.CPU, OC_MAX_FREQ, context);
    }

    public static RailReadback getRailReadback() {
        String railDump = Utils.readFile(VOLTAGE);
        return new RailReadback(getValue(VPROC, railDump), getValue(VSRAM, railDump));
    }

    private static int getValue(Pattern pattern, String text) {
        if (text == null) return 0;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Utils.strToInt(matcher.group(1)) : 0;
    }
}
