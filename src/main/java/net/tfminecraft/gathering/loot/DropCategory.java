package net.tfminecraft.gathering.loot;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class DropCategory {

    public static final class Entry {
        public final String type;
        public final int minAmount;
        public final int maxAmount;
        public final double weight;

        public Entry(String type, int minAmount, int maxAmount, double weight) {
            this.type = type;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.weight = weight;
        }

        public int rollAmount(ThreadLocalRandom rng) {
            if (minAmount >= maxAmount) return Math.max(0, minAmount);
            return rng.nextInt(minAmount, maxAmount + 1);
        }
    }

    public static final class Drop {
        public final String type;
        public final int amount;

        public Drop(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }
    }

    private final String name;
    private final List<Entry> entries;

    public DropCategory(String name, List<String> lines) {
        this.name = name;
        this.entries = parseLines(lines);
    }

    public String getName() {
        return name;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Drop rollOne(ThreadLocalRandom rng) {
        Entry entry = pickOne(rng);
        if (entry == null) return null;
        int amount = entry.rollAmount(rng);
        if (amount <= 0) return null;
        return new Drop(entry.type, amount);
    }

    public List<Drop> rollMany(int rolls, ThreadLocalRandom rng) {
        int count = Math.max(0, rolls);
        List<Drop> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Drop drop = rollOne(rng);
            if (drop != null) out.add(drop);
        }
        return out;
    }

    public Entry pickOne(ThreadLocalRandom rng) {
        if (entries.isEmpty()) return null;
        double total = 0.0;
        for (Entry e : entries) {
            total += Math.max(0.0, e.weight);
        }
        if (total <= 0.0) return null;
        double r = rng.nextDouble() * total;
        double acc = 0.0;
        for (Entry e : entries) {
            acc += Math.max(0.0, e.weight);
            if (r <= acc) return e;
        }
        return entries.get(entries.size() - 1);
    }

    private List<Entry> parseLines(List<String> lines) {
        if (lines == null) return Collections.emptyList();
        List<Entry> list = new ArrayList<>();
        for (String raw : lines) {
            Entry e = parseLine(raw);
            if (e != null) list.add(e);
        }
        return Collections.unmodifiableList(list);
    }

    private Entry parseLine(String line) {
        if (line == null) return null;
        String trimmed = stripComment(line.trim());
        if (trimmed.isEmpty()) return null;

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            Bukkit.getLogger().warning("[DropCategory:" + name + "] Bad line (need type min-max weight): " + line);
            return null;
        }

        String type = parts[0];
        int dash = parts[1].indexOf('-');
        if (dash <= 0) {
            Bukkit.getLogger().warning("[DropCategory:" + name + "] Bad range (use min-max): " + line);
            return null;
        }

        int min, max;
        try {
            min = Integer.parseInt(parts[1].substring(0, dash));
            max = Integer.parseInt(parts[1].substring(dash + 1));
            if (min > max) {
                int t = min;
                min = max;
                max = t;
            }
        } catch (NumberFormatException ex) {
            Bukkit.getLogger().warning("[DropCategory:" + name + "] Bad numbers in range: " + line);
            return null;
        }

        double weight;
        try {
            weight = Double.parseDouble(parts[2]);
        } catch (NumberFormatException ex) {
            Bukkit.getLogger().warning("[DropCategory:" + name + "] Bad weight: " + line);
            return null;
        }

        return new Entry(type, Math.max(0, min), Math.max(0, max), Math.max(0.0, weight));
    }

    private String stripComment(String s) {
        int i = s.indexOf('#');
        return (i >= 0) ? s.substring(0, i).trim() : s;
    }
}
