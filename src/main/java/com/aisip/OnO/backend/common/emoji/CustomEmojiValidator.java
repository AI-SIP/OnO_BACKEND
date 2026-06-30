package com.aisip.OnO.backend.common.emoji;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CustomEmojiValidator {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "angry_with_flames",
            "crying_in_rain",
            "confused_question",
            "cool_sunglasses",
            "happy_tears",
            "excited_happy",
            "lol_laughing_tears",
            "cheek_to_cheek",
            "dizzy_spiral_eyes2",
            "stressed_bomb",
            "shy_clasped_hands",
            "shy_wink_heart",
            "in_love_heart",
            "scared_dread",
            "star_eyes_excited",
            "frustrated_studying",
            "holding_pen",
            "reading_with_glasses",
            "reading_tablet",
            "studying_together",
            "studying_with_lamp",
            "idea_lightbulb",
            "got_100_score",
            "success_checkmark",
            "writing_wink",
            "working_laptop",
            "birthday_cake",
            "celebrating_confetti",
            "cheering_megaphone",
            "excited_with_gift",
            "fired_up_sparkle_eyes",
            "superhero_cape",
            "gold_medal",
            "holding_flower",
            "holding_heart",
            "lifting_weights",
            "party_celebration",
            "praying_grateful",
            "sprout_growth",
            "trophy_celebration",
            "thumbs_up_happy",
            "thumbs_up_wink",
            "thank_you_sign",
            "winking_fist",
            "hi_greeting",
            "waving_hello",
            "cheers_beer",
            "cuddle_love",
            "texting_heart",
            "puzzle_teamwork",
            "peeking_wall",
            "blue_hoodie",
            "sleeping_blanket",
            "cozy_blanket",
            "cooking_chef",
            "drinking_coffee",
            "sleeping_with_star",
            "eating_ice_cream",
            "eating_skewer",
            "playing_game_controller",
            "hiking_backpack",
            "listening_headphones",
            "morning_coffee",
            "shopping_bags",
            "singing_happy",
            "taking_bath",
            "taking_photo",
            "umbrella_rain",
            "wearing_scarf"
    );

    public void validate(String emojiKey) {
        if (!isAllowed(emojiKey)) {
            throw new ApplicationException(CustomEmojiErrorCase.INVALID_EMOJI_KEY);
        }
    }

    public void validateNullable(String emojiKey) {
        if (emojiKey != null) {
            validate(emojiKey);
        }
    }

    public boolean isAllowed(String emojiKey) {
        return emojiKey != null && ALLOWED_KEYS.contains(emojiKey);
    }
}
