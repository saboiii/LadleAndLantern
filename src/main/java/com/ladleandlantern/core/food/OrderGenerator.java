package com.ladleandlantern.core.food;

import com.ladleandlantern.core.restaurant.Course;
import com.ladleandlantern.core.restaurant.CustomerProfile;
import com.ladleandlantern.core.restaurant.Restaurant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.*;

public final class OrderGenerator {
    private static final int UNLOCKS_PER_STAR = 2;

    private OrderGenerator() {}

    public record Mood(boolean warming, boolean kid) {}

    public static List<Order.Line> generate(Restaurant r, CustomerProfile profile, long day, int seasonLength, RandomSource random, Mood mood) {
        Map<ResourceLocation, Course> available = new LinkedHashMap<>(r.available(day, seasonLength));

        if (mood.kid()) available.keySet().removeIf(FoodRules::isAlcoholic);
        if (available.isEmpty()) return List.of();
        List<ResourceLocation> seasonalToday = r.seasonalToday(day, seasonLength);
        Map<ResourceLocation, Integer> weights = weights(r, profile, available, seasonalToday, day);
        if (mood.warming()) weights.replaceAll((id, w) -> FoodRules.isWarming(id) ? w * 3 : w);
        if (mood.kid()) weights.replaceAll((id, w) -> available.get(id) == Course.DESSERT || available.get(id) == Course.DRINK ? w * 3 : w);

        List<Course> courses = new ArrayList<>();
        for (Course c : Course.values()) if (random.nextFloat() < c.chance && hasCourse(weights, available, c)) courses.add(c);
        if (courses.isEmpty()) {
            List<Course> possible = Arrays.stream(Course.values()).filter(c -> hasCourse(weights, available, c)).toList();
            if (possible.isEmpty()) return List.of();
            courses.add(possible.get(random.nextInt(possible.size())));
        }

        List<Order.Line> lines = new ArrayList<>();
        for (Course c : courses) {
            ResourceLocation pick = weightedPick(weights, available, c, random);
            if (pick != null) lines.add(new Order.Line(pick, c));
        }
        return lines;
    }

    private static Map<ResourceLocation, Integer> weights(Restaurant r, CustomerProfile profile,
            Map<ResourceLocation, Course> available, List<ResourceLocation> seasonal, long day) {
        Map<ResourceLocation, Integer> w = new LinkedHashMap<>();

        available.keySet().forEach(id -> w.put(id, 1));
        List<ResourceLocation> others = new ArrayList<>(available.keySet());
        others.removeAll(profile.favourites);
        Collections.shuffle(others, new Random(profile.id.getLeastSignificantBits()));
        others.stream().limit((long) profile.stars * UNLOCKS_PER_STAR).forEach(id -> w.put(id, 2));
        seasonal.forEach(id -> w.merge(id, 3, Math::max));

        List<ResourceLocation> menu = new ArrayList<>(available.keySet());
        if (!menu.isEmpty()) {
            Random daily = new Random(day * 31 + profile.id.getMostSignificantBits());
            w.merge(menu.get(daily.nextInt(menu.size())), 3, Math::max);
        }
        profile.favourites.forEach(id -> w.merge(id, 4, Math::max));
        if (r.special != null && available.containsKey(r.special)) w.merge(r.special, 4, Math::max);
        w.keySet().retainAll(available.keySet());

        if (w.isEmpty()) available.keySet().forEach(id -> w.put(id, 1));
        return w;
    }

    private static boolean hasCourse(Map<ResourceLocation, Integer> weights, Map<ResourceLocation, Course> available, Course c) {
        return weights.keySet().stream().anyMatch(id -> available.get(id) == c);
    }

    private static ResourceLocation weightedPick(Map<ResourceLocation, Integer> weights, Map<ResourceLocation, Course> available,
            Course c, RandomSource random) {
        int total = 0;
        for (var e : weights.entrySet()) if (available.get(e.getKey()) == c) total += e.getValue();
        if (total == 0) return null;
        int roll = random.nextInt(total);
        for (var e : weights.entrySet()) {
            if (available.get(e.getKey()) != c) continue;
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return null;
    }
}
