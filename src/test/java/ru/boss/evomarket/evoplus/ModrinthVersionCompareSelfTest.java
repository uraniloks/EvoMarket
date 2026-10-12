package ru.boss.evomarket.evoplus;

import ru.boss.evomarket.core.VersionOrder;

/** Dependency-free version ordering checks for the Modrinth update checker. */
public final class ModrinthVersionCompareSelfTest {
    public static void main(String[] args) {
        check("0.1.17", "0.1.16", 1);
        check("0.1.16", "0.1.15", 1);
        check("0.1.10", "0.1.9", 1);
        check("0.2.0", "0.1.99", 1);
        check("0.1.16-beta.1", "0.1.16", -1);
        check("v0.1.16", "0.1.16", 0);
        check("0.1.16+build.3", "0.1.16", 0);
        check("0.1.16-beta.10", "0.1.16-beta.2", 1);
        check("0.1.15", "0.1.16", -1);
        System.out.println("ModrinthVersionCompareSelfTest: all checks passed");
    }

    private static void check(String left, String right, int expectedSign) {
        int actual = Integer.signum(VersionOrder.compare(left, right));
        if (actual != Integer.signum(expectedSign)) {
            throw new AssertionError(left + " compared to " + right + ": expected sign "
                    + expectedSign + " but got " + actual);
        }
    }
}
