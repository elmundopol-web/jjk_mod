package com.pop.jjk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JJKRoster {

    public static final String NONE = "none";
    private static final Map<String, TechniqueDefinition> TECHNIQUES = new LinkedHashMap<>();
    private static final Map<String, CharacterDefinition> CHARACTERS = new LinkedHashMap<>();

    static {
        registerTechnique("blue", "technique.jjk.blue", "technique.jjk.blue_desc", "technique.jjk.blue_hint", 0x3FA7FF, true);
        registerTechnique("red", "technique.jjk.red", "technique.jjk.red_desc", "technique.jjk.red_hint", 0xFF553D, true);
        registerTechnique("purple", "technique.jjk.purple", "technique.jjk.purple_desc", "technique.jjk.purple_hint", 0xB66CFF, true);
        registerTechnique("infinite_domain", "technique.jjk.infinite_domain", "technique.jjk.infinite_domain_desc", "technique.jjk.infinite_domain_hint", 0x101820, true);
        registerTechnique("infinity", "technique.jjk.infinity", "technique.jjk.infinity_desc", "technique.jjk.infinity_hint", 0xCCF0FF, true);
        registerTechnique("flash_step", "technique.jjk.flash_step", "technique.jjk.flash_step_desc", "technique.jjk.flash_step_hint", 0xF4F1B5, false);
        registerTechnique("diverging_fist", "technique.jjk.diverging_fist", "technique.jjk.diverging_fist_desc", "technique.jjk.diverging_fist_hint", 0xE7A95F, true);
        registerTechnique("black_flash", "technique.jjk.black_flash", "technique.jjk.black_flash_desc", "technique.jjk.black_flash_hint", 0x2C2C2C, false);
        registerTechnique("rush_combo", "technique.jjk.rush_combo", "technique.jjk.rush_combo_desc", "technique.jjk.rush_combo_hint", 0xD95F43, false);
        registerTechnique("dismantle", "technique.jjk.dismantle", "technique.jjk.dismantle_desc", "technique.jjk.dismantle_hint", 0xC94848, false);
        registerTechnique("cleave", "technique.jjk.cleave", "technique.jjk.cleave_desc", "technique.jjk.cleave_hint", 0xF08080, false);
        registerTechnique("malevolent_shrine", "technique.jjk.malevolent_shrine", "technique.jjk.malevolent_shrine_desc", "technique.jjk.malevolent_shrine_hint", 0x8B2A2A, false);
        registerTechnique("divine_dog", "technique.jjk.divine_dog", "technique.jjk.divine_dog_desc", "technique.jjk.divine_dog_hint", 0x8AA5C7, false);
        registerTechnique("nue", "technique.jjk.nue", "technique.jjk.nue_desc", "technique.jjk.nue_hint", 0x7282D8, false);
        registerTechnique("max_elephant", "technique.jjk.max_elephant", "technique.jjk.max_elephant_desc", "technique.jjk.max_elephant_hint", 0x5F86A6, false);
        registerTechnique("boogie_woogie", "technique.jjk.boogie_woogie", "technique.jjk.boogie_woogie_desc", "technique.jjk.boogie_woogie_hint", 0xA76B3F, false);
        registerTechnique("close_quarters", "technique.jjk.close_quarters", "technique.jjk.close_quarters_desc", "technique.jjk.close_quarters_hint", 0xD79A61, false);
        registerTechnique("piercing_blood", "technique.jjk.piercing_blood", "technique.jjk.piercing_blood_desc", "technique.jjk.piercing_blood_hint", 0xCC1010, true);
        registerTechnique("flowing_red_scale", "technique.jjk.flowing_red_scale", "technique.jjk.flowing_red_scale_desc", "technique.jjk.flowing_red_scale_hint", 0xFF4040, true);
        registerTechnique("supernova", "technique.jjk.supernova", "technique.jjk.supernova_desc", "technique.jjk.supernova_hint", 0x8B0000, true);

        registerCharacter("gojo", "character.jjk.gojo", "character.jjk.gojo_desc", 0x9EEBFF, List.of("blue", "red", "purple", "infinite_domain", "infinity"), true);
        registerCharacter("itadori", "character.jjk.itadori", "character.jjk.itadori_desc", 0xE78C5A, List.of("diverging_fist", "black_flash", "rush_combo"), false);
        registerCharacter("sukuna", "character.jjk.sukuna", "character.jjk.sukuna_desc", 0xC44E4E, List.of("dismantle", "cleave", "malevolent_shrine"), false);
        registerCharacter("megumi", "character.jjk.megumi", "character.jjk.megumi_desc", 0x6A84C8, List.of("divine_dog", "nue", "max_elephant"), false);
        registerCharacter("todo", "character.jjk.todo", "character.jjk.todo_desc", 0xD09C67, List.of("boogie_woogie", "black_flash", "close_quarters"), false);
        registerCharacter("choso", "character.jjk.choso", "character.jjk.choso_desc", 0xCC1010, List.of("piercing_blood", "flowing_red_scale", "supernova"), false);
    }

    private JJKRoster() {
    }

    private static void registerTechnique(String id, String nameKey, String descriptionKey, String hintKey, int accentColor, boolean implemented) {
        TECHNIQUES.put(id, new TechniqueDefinition(id, nameKey, descriptionKey, hintKey, accentColor, implemented));
    }

    private static void registerCharacter(String id, String nameKey, String descriptionKey, int accentColor, List<String> activeTechniqueIds, boolean supportsInfinity) {
        CHARACTERS.put(id, new CharacterDefinition(id, nameKey, descriptionKey, accentColor, activeTechniqueIds, supportsInfinity));
    }

    public static List<CharacterDefinition> characters() {
        return List.copyOf(CHARACTERS.values());
    }

    public static CharacterDefinition getCharacter(String id) {
        return CHARACTERS.get(id);
    }

    public static boolean isValidCharacter(String id) {
        return CHARACTERS.containsKey(id);
    }

    public static TechniqueDefinition getTechnique(String id) {
        return TECHNIQUES.get(id);
    }

    public static List<TechniqueDefinition> techniquesForCharacter(String characterId) {
        CharacterDefinition character = getCharacter(characterId);

        if (character == null) {
            return List.of();
        }

        return character.activeTechniqueIds().stream()
            .map(JJKRoster::getTechnique)
            .toList();
    }

    public record TechniqueDefinition(
        String id,
        String nameKey,
        String descriptionKey,
        String hintKey,
        int accentColor,
        boolean implemented
    ) {
    }

    public record CharacterDefinition(
        String id,
        String nameKey,
        String descriptionKey,
        int accentColor,
        List<String> activeTechniqueIds,
        boolean supportsInfinity
    ) {
    }
}
