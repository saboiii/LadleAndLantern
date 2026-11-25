package com.ladleandlantern.core.food;

import com.ladleandlantern.core.config.Config;
import net.minecraft.util.RandomSource;

import java.util.List;

public final class Names {
    private static final List<String> FIRST = List.of(
            "Aisha", "Priya", "Wei Ling", "Hana", "Mei", "Zara", "Nadia", "Farah", "Sofia", "Amara", "Lina", "Yuki",
            "Isla", "Noor", "Ananya", "Chloe", "Ines", "Leila", "Ruth", "Grace", "Siti", "Divya", "Elena", "Maya",
            "Arjun", "Hafiz", "Kai", "Ravi", "Jun", "Omar", "Mateo", "Theo", "Ethan", "Bilal", "Daniel", "Hiro",
            "Felix", "Rohan", "Imran", "Luca", "Wen Jie", "Sami", "Noah", "Idris", "Kenji", "Marcus", "Aidan", "Zayd");
    private static final List<String> SURNAMES = List.of(
            "Tan", "Lim", "Rahman", "Iyer", "Okafor", "Moreau", "Nakamura", "Fernandes", "Kowalski", "Haddad", "Wong", "Ng");
    private static final List<String> CLOSERS = List.of(
            "Sir Crumbsworth", "Madame Ladle", "Old Man Kettle", "The Night Owl", "Baron von Brioche",
            "Auntie Sambal", "Duchess Dumpling", "Captain Saucepan");

    private static final List<String> KIDS = List.of("Pip", "Bea", "Ollie", "Mimi", "Tomo", "Lulu", "Benji", "Ayla", "Sunny", "Kiki");

    private static final List<String> STAFF_WOMEN = List.of("Aisha", "Priya", "Hana", "Mei", "Zara", "Nadia", "Farah", "Sofia",
            "Amara", "Lina", "Yuki", "Isla", "Noor", "Chloe", "Ines", "Leila", "Ruth", "Grace", "Siti", "Divya", "Elena", "Maya", "Wei Ling");
    private static final List<String> STAFF_MEN = List.of("Arjun", "Hafiz", "Kai", "Ravi", "Jun", "Omar", "Mateo", "Theo", "Ethan",
            "Bilal", "Daniel", "Hiro", "Felix", "Rohan", "Imran", "Luca", "Wen Jie", "Sami", "Noah", "Idris", "Kenji", "Marcus", "Aidan", "Zayd");

    private Names() {}

    public static String staff(RandomSource r, boolean woman) {
        List<String> names = woman ? STAFF_WOMEN : STAFF_MEN;
        return names.get(r.nextInt(names.size()));
    }

    public static String staffSkin(RandomSource r, boolean woman) {
        return "staff:" + (woman ? "waitress_" : "waiter_") + (1 + r.nextInt(3));
    }

    public static String kid(RandomSource r) {
        return KIDS.get(r.nextInt(KIDS.size()));
    }

    public static String regular(RandomSource r) {
        return FIRST.get(r.nextInt(FIRST.size()));
    }

    public static String critic(RandomSource r) {
        return "Critic " + SURNAMES.get(r.nextInt(SURNAMES.size()));
    }

    public static String closer(RandomSource r) {
        return CLOSERS.get(r.nextInt(CLOSERS.size()));
    }

    public static String skin(RandomSource r) {
        List<? extends String> skins = Config.SKINS.get();

        if (!skins.isEmpty() && r.nextFloat() < 0.25f) return "user:" + skins.get(r.nextInt(skins.size()));
        return "local:" + r.nextInt(1_000_000);
    }
}
