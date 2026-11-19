/*
 * Copyright (C) 2026
 *
 * This file is part of Kernel Adiutor.
 */
package com.grarak.kerneladiutor.utils.kernel.gpu;

import android.content.Context;

import com.grarak.kerneladiutor.fragments.ApplyOnBootFragment;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.root.Control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MT6765 Segment-3 GPU OC ceiling and physical VCORE readback. */
public final class T227UGPU {

    private static final String OPP_DUMP = "/proc/gpufreq/gpufreq_opp_dump";
    private static final String VAR_DUMP = "/proc/gpufreq/gpufreq_var_dump";
    private static final String OC_MAX_FREQ = "/proc/gpufreq/gpufreq_oc_max_freq";
    private static final String OC_OPP_VOLTAGE = "/proc/gpufreq/gpufreq_oc_opp_voltage";
    private static final Pattern FREQUENCY = Pattern.compile("freq\\s*=\\s*(\\d+)");
    private static final Pattern MAX_FREQUENCY = Pattern.compile("max_freq_khz\\s*=\\s*(\\d+)");
    private static final Pattern REAL_FREQUENCY = Pattern.compile("real freq\\s*=\\s*(\\d+)");
    private static final Pattern REAL_VOLTAGE = Pattern.compile("real volt\\s*=\\s*(\\d+)");

    public static final class OppVoltage {
        private final int mFrequency;
        private final int mAssignedMillivolts;
        private final int mStockMillivolts;

        private OppVoltage(int frequency, int assignedMillivolts, int stockMillivolts) {
            mFrequency = frequency;
            mAssignedMillivolts = assignedMillivolts;
            mStockMillivolts = stockMillivolts;
        }

        public int getFrequency() {
            return mFrequency;
        }

        public int getAssignedMillivolts() {
            return mAssignedMillivolts;
        }

        public int getStockMillivolts() {
            return mStockMillivolts;
        }
    }

    private T227UGPU() {
    }

    public static boolean supported() {
        return Utils.existFile(OPP_DUMP) && Utils.existFile(VAR_DUMP)
                && Utils.existFile(OC_MAX_FREQ);
    }

    public static List<Integer> getFrequencies() {
        List<Integer> frequencies = new ArrayList<>();
        String dump = Utils.readFile(OPP_DUMP);
        if (dump == null) return frequencies;

        Matcher matcher = FREQUENCY.matcher(dump);
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
        return getValue(MAX_FREQUENCY, Utils.readFile(OC_MAX_FREQ));
    }

    public static int getCurrentFrequency() {
        return getValue(REAL_FREQUENCY, Utils.readFile(VAR_DUMP));
    }

    /** This reads the VCORE regulator, not an OPP-table value. */
    public static int getLiveVcoreMillivolts() {
        return getValue(REAL_VOLTAGE, Utils.readFile(VAR_DUMP)) / 100;
    }

    public static boolean hasOppVoltageControl() {
        return supported() && Utils.existFile(OC_OPP_VOLTAGE)
                && !getOppVoltages().isEmpty();
    }

    public static List<OppVoltage> getOppVoltages() {
        List<OppVoltage> voltages = new ArrayList<>();
        String table = Utils.readFile(OC_OPP_VOLTAGE);
        if (table == null) return voltages;

        for (String line : table.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] values = line.split("\\s+");
            if (values.length != 3) continue;
            voltages.add(new OppVoltage(Utils.strToInt(values[0]),
                    Utils.strToInt(values[1]), Utils.strToInt(values[2])));
        }
        return voltages;
    }

    public static List<String> getVoltageChoices(OppVoltage voltage) {
        List<String> choices = new ArrayList<>();
        for (int mv = 700; mv <= voltage.getStockMillivolts(); mv += 5) {
            choices.add(String.valueOf(mv));
        }
        addVoltageChoice(choices, voltage.getAssignedMillivolts());
        addVoltageChoice(choices, voltage.getStockMillivolts());
        Collections.sort(choices, (left, right) -> Utils.strToInt(left) - Utils.strToInt(right));
        return choices;
    }

    public static void setOppVoltage(OppVoltage voltage, int millivolts, Context context) {
        String id = OC_OPP_VOLTAGE + ":" + voltage.getFrequency();
        Control.runSetting(Control.write(voltage.getFrequency() + " " + millivolts,
                OC_OPP_VOLTAGE), ApplyOnBootFragment.GPU, id, context);
    }

    public static void resetOppVoltage(OppVoltage voltage, Context context) {
        setOppVoltage(voltage, 0, context);
    }

    public static void setMaximumFrequency(int frequency, Context context) {
        Control.runSetting(Control.write(String.valueOf(frequency), OC_MAX_FREQ),
                ApplyOnBootFragment.GPU, OC_MAX_FREQ, context);
    }

    private static int getValue(Pattern pattern, String text) {
        if (text == null) return 0;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Utils.strToInt(matcher.group(1)) : 0;
    }

    private static void addVoltageChoice(List<String> choices, int millivolts) {
        String value = String.valueOf(millivolts);
        if (millivolts > 0 && !choices.contains(value)) choices.add(value);
    }
}
