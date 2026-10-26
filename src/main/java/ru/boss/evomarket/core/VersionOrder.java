package ru.boss.evomarket.core;

import java.util.regex.Matcher;
import java.math.BigInteger;
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
            BigInteger l = i < leftParts.length ? new BigInteger(leftParts[i]) : BigInteger.ZERO;
            BigInteger r = i < rightParts.length ? new BigInteger(rightParts[i]) : BigInteger.ZERO;
            if (!l.equals(r)) return l.compareTo(r);
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
                int c = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
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

}
