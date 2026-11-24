package com.ladleandlantern.core.entity;

import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.restaurant.CustomerProfile;
import com.ladleandlantern.core.restaurant.CustomerType;
import net.minecraft.util.RandomSource;

import java.util.List;

public final class CustomerLines {
    private CustomerLines() {}

    private enum Personality {
        CHEERFUL(List.of("Everything on here sounds lovely.", "I can't decide, it all looks so good.", "This place always cheers me up."),
                "Could I see a menu, please?"),
        SHY(List.of("Oh, um, I'm still looking.", "Sorry, I'm a slow reader.", "Just a little longer, if that's alright."),
                "Um, may I have a menu?"),
        PARTICULAR(List.of("I like to know exactly what I'm getting.", "I'm reading the small print, as always.", "Hmm. Everything in its place."),
                "A menu, when you have a moment."),
        CHATTY(List.of("My neighbour told me to try this place.", "Did you hear about the new bakery in town?", "Honestly, I come for the company."),
                "Oh, hello. A menu would be lovely."),
        DREAMY(List.of("I love how the light comes in here.", "I could sit here all afternoon.", "Reading menus is half the fun."),
                "Is there a menu somewhere?"),
        PECKISH(List.of("My stomach's rumbling just looking at this.", "I skipped lunch for this.", "Everything, please. Well, almost."),
                "Menu, please. I'm starving.");

        final List<String> browsing;
        final String wantsMenu;

        Personality(List<String> browsing, String wantsMenu) {
            this.browsing = browsing;
            this.wantsMenu = wantsMenu;
        }
    }

    private static final List<String> CRITIC = List.of("Hmm. Interesting choices.", "I'm taking notes, don't mind me.", "Let's see what this kitchen can do.");
    private static final List<String> CLOSER = List.of("I know it's late. I'll be quick.", "Last stop of my day.", "Sorry, I always come in right before closing.");
    private static final List<String> KID = List.of("Do you have anything with sprinkles?", "I'm choosing all by myself.", "Mum says I can have pudding if I'm good.");

    private static Personality personality(CustomerProfile p) {
        return Personality.values()[Math.floorMod(p.id.hashCode(), Personality.values().length)];
    }

    public static String wantsMenu(CustomerProfile p, CustomerType type) {
        if (p.kid) return "Can I have a menu with pictures?";
        if (type == CustomerType.CRITIC) return "The menu, if you please.";
        if (type == CustomerType.CLOSER) return "A quick menu before you close?";
        return personality(p).wantsMenu;
    }

    public static String browsing(CustomerProfile p, CustomerType type, RandomSource random) {
        if (p.kid) return KID.get(random.nextInt(KID.size()));
        if (type == CustomerType.CRITIC) return CRITIC.get(random.nextInt(CRITIC.size()));
        if (type == CustomerType.CLOSER) return CLOSER.get(random.nextInt(CLOSER.size()));
        if (p.visits >= 5 && random.nextInt(4) == 0) return "Back again. You know me by now.";
        if (!p.favourites.isEmpty() && random.nextInt(4) == 0) {
            return "I'm hoping the " + FoodRules.nameOf(p.favourites.get(random.nextInt(p.favourites.size()))) + " is on today.";
        }
        List<String> lines = personality(p).browsing;
        return lines.get(random.nextInt(lines.size()));
    }

    public static String ready(CustomerProfile p, RandomSource random) {
        if (p.kid) return "I know what I want.";
        return switch (personality(p)) {
            case SHY -> "I think I'm ready, if you are.";
            case PECKISH -> "Ready. Very ready.";
            case PARTICULAR -> "I've decided. Finally.";
            default -> random.nextBoolean() ? "Ready when you are." : "I think I've decided.";
        };
    }
}
