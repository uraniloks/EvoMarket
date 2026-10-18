package ru.boss.evomarket.core;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SellerListStoreSelfTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("evomarket-seller-lists");
        Path favoritesPath = directory.resolve("favorites.txt");
        SellerListStore favorites = new SellerListStore(favoritesPath);
        check(favorites.add("PlayerOne"), "first add should succeed");
        check(!favorites.add("playerone"), "names should be case-insensitive");
        check(favorites.contains("PLAYERONE"), "contains should ignore case");
        check(new SellerListStore(favoritesPath).contains("playerone"), "list should persist across reloads");
        check(favorites.remove("PLAYERONE"), "remove should ignore case");
        check(!favorites.contains("playerone"), "removed seller should no longer be present");

        Path ignoredPath = directory.resolve("ignored.txt");
        SellerListStore ignored = new SellerListStore(ignoredPath);
        ignored.add("BadSeller");
        ignored.clear();
        check(ignored.names().isEmpty(), "clear should remove all ignored sellers");
        check(new SellerListStore(ignoredPath).names().isEmpty(), "clear should persist");
        System.out.println("SellerListStoreSelfTest: all checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
