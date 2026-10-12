package ru.boss.evomarket.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small semantic-version comparator for numeric versions and prerelease labels. */
public final class VersionOrder {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)^v?(\\d+(?:\\.\\d+)*)(?:-([^+]+))?(?:\\+[^+]+)?$"
    );

    private VersionOrder() {}

    /** Returns a positive value if left is newer, a negative value if right is newer. */
    public static int compare(String left, String right) {
        String safeLeft = left == null ? "" : left.trim();
        String safeRight = right == null ? "" : right.trim();
        Matcher lm = VERSION_PATTERN.matcher(safeLeft);
        Matcher rm = VERSION_PATTERN.matcher(safeRight);
        if (!lm.matches() || !rm.matches()) return safeLeft.compareToIgnoreCase(safeRight);

        String[] leftParts = lm.group(1).split("\\.");
        String[] rightParts = rm.group(1).split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            long l = i < leftParts.length ? parseLong(leftParts[i]) : 0;
            long r = i < rightParts.length ? parseLong(rightParts[i]) : 0;
            if (l != r) return Long.compare(l, r);
        }

        String leftPre = lm.group(2);
        String rightPre = rm.group(2);
        if (leftPre == null && rightPre != null) return 1;
        if (leftPre != null && rightPre == null) return -1;
        if (leftPre == null) return 0;
        return comparePrerelease(leftPre, rightPre);
    }

    private static int comparePrerelease(String left, String right) {
        String[] a = left.split("[.-]");
        String[] b = right.split("[.-]");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            if (i >= a.length) return -1;
            if (i >= b.length) return 1;
            boolean an = a[i].matches("\\d+");
            boolean bn = b[i].matches("\\d+");
            if (an && bn) {
                int c = Long.compare(parseLong(a[i]), parseLong(b[i]));
                if (c != 0) return c;
            } else if (an != bn) {
                return an ? -1 : 1;
            } else {
                int c = a[i].compareToIgnoreCase(b[i]);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
