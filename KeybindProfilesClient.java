package de.swegbu.keybindprofiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class KeybindProfilesClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config/keybind-profiles");
    private static final Path FILE = DIR.resolve("profiles.json");
    private static final Type PROFILE_MAP_TYPE = new TypeToken<LinkedHashMap<String, Profile>>() {}.getType();

    private static final Map<String, Profile> PROFILES = new LinkedHashMap<>();
    private static String activeProfile = "";
    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybindprofiles.open_menu",
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));
        loadFromDisk();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                client.gui.setScreen(new ProfileScreen(null));
            }
        });
    }

    static Map<String, Profile> profiles() { return PROFILES; }
    static String activeProfile() { return activeProfile; }

    static void saveToDisk() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(PROFILES), StandardCharsets.UTF_8);
            Files.writeString(DIR.resolve("active.txt"), activeProfile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadFromDisk() {
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                Map<String, Profile> loaded = GSON.fromJson(Files.readString(FILE), PROFILE_MAP_TYPE);
                if (loaded != null) PROFILES.putAll(loaded);
            }
            Path active = DIR.resolve("active.txt");
            if (Files.exists(active)) activeProfile = Files.readString(active, StandardCharsets.UTF_8).trim();
            if (PROFILES.isEmpty()) {
                PROFILES.put("Default", captureCurrent());
                activeProfile = "Default";
                saveToDisk();
            }
            if (PROFILES.containsKey(activeProfile)) apply(PROFILES.get(activeProfile));
            else activeProfile = "";
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Profile captureCurrent() {
        Profile p = new Profile();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            p.keys.put(mapping.getName(), encode(mapping));
        }
        return p;
    }

    static void apply(Profile profile) {
        if (profile == null) return;
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping mapping : mc.options.keyMappings) {
            String value = profile.keys.get(mapping.getName());
            if (value != null) {
                try {
                    mapping.setKey(parse(value));
                } catch (RuntimeException ignored) {}
            }
        }
        KeyMapping.resetMapping();
        mc.options.save();
    }

    static String encode(KeyMapping mapping) {
        return mapping.saveString();
    }

    static String encodeKey(KeyMapping mapping) { return encode(mapping); }

    static com.mojang.blaze3d.platform.InputConstants.Key parse(String value) {
        return com.mojang.blaze3d.platform.InputConstants.getKey(value);
    }

    static void createProfile(String name) {
        String clean = name.trim();
        if (clean.isEmpty() || PROFILES.containsKey(clean)) return;
        PROFILES.put(clean, captureCurrent());
        activeProfile = clean;
        saveToDisk();
    }

    static void saveCurrentTo(String name) {
        if (!PROFILES.containsKey(name)) return;
        PROFILES.put(name, captureCurrent());
        activeProfile = name;
        saveToDisk();
    }

    static void activate(String name) {
        Profile profile = PROFILES.get(name);
        if (profile == null) return;
        apply(profile);
        activeProfile = name;
        saveToDisk();
    }

    static void delete(String name) {
        PROFILES.remove(name);
        if (Objects.equals(activeProfile, name)) activeProfile = "";
        saveToDisk();
    }

    static void rename(String oldName, String newName) {
        String clean = newName.trim();
        if (clean.isEmpty() || PROFILES.containsKey(clean) || !PROFILES.containsKey(oldName)) return;
        LinkedHashMap<String, Profile> copy = new LinkedHashMap<>();
        for (var entry : PROFILES.entrySet()) {
            copy.put(entry.getKey().equals(oldName) ? clean : entry.getKey(), entry.getValue());
        }
        PROFILES.clear();
        PROFILES.putAll(copy);
        if (Objects.equals(activeProfile, oldName)) activeProfile = clean;
        saveToDisk();
    }

    static void exportProfile(String name) {
        Profile profile = PROFILES.get(name);
        if (profile == null) return;
        try {
            Files.createDirectories(DIR.resolve("exports"));
            String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
            Files.writeString(DIR.resolve("exports").resolve(safe + ".json"),
                    GSON.toJson(profile), StandardCharsets.UTF_8);
        } catch (IOException e) { e.printStackTrace(); }
    }

    static void importProfile(String filename) {
        try {
            Path path = DIR.resolve("exports").resolve(filename).normalize();
            if (!path.startsWith(DIR.resolve("exports").normalize()) || !Files.exists(path)) return;
            Profile p = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Profile.class);
            if (p == null) return;
            String base = path.getFileName().toString().replaceFirst("\\.json$", "");
            String name = base;
            int i = 2;
            while (PROFILES.containsKey(name)) name = base + " (" + i++ + ")";
            PROFILES.put(name, p);
            saveToDisk();
        } catch (Exception e) { e.printStackTrace(); }
    }

    static class Profile {
        Map<String, String> keys = new LinkedHashMap<>();
    }

    static class ProfileScreen extends Screen {
        private final Screen parent;
        private String status = "Profile wählen, erstellen oder exportieren.";
        private String newName = "";
        private String renameName = "";
        private int scroll = 0;

        ProfileScreen(Screen parent) {
            super(Component.literal("Keybind Profiles"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int left = this.width / 2 - 150;
            int top = 38;
            int y = top;
            for (String name : PROFILES.keySet()) {
                if (y > this.height - 100) break;
                String shown = name.equals(activeProfile) ? "✓ " + name + " (aktiv)" : name;
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(shown), b -> { activate(name); status = "Geladen: " + name; })
                        .bounds(left, y, 180, 20).build());
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Bearbeiten"), b -> {
                            activate(name);
                            Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.options.controls.ControlsScreen(this, Minecraft.getInstance().options));
                        }).bounds(left + 185, y, 85, 20).build());
                y += 24;
            }

            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Profil erstellen"), b -> {
                String name = newName.trim();
                if (name.isEmpty()) { status = "Bitte zuerst einen Profilnamen eingeben."; return; }
                if (PROFILES.containsKey(name)) { status = "Dieser Name existiert bereits."; return; }
                createProfile(name);
                status = "Erstellt: " + name;
                rebuildWidgets();
            }).bounds(left, this.height - 88, 130, 20).build());

            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Aktuelle Tasten speichern"), b -> {
                if (PROFILES.containsKey(activeProfile)) {
                    saveCurrentTo(activeProfile);
                    status = "Gespeichert: " + activeProfile;
                } else status = "Erstelle zuerst ein Profil.";
            }).bounds(left + 135, this.height - 88, 135, 20).build());

            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Export"), b -> {
                if (!activeProfile.isEmpty()) { exportProfile(activeProfile); status = "Export in config/keybind-profiles/exports"; }
                else status = "Bitte zuerst ein Profil laden.";
            }).bounds(left, this.height - 62, 85, 20).build());

            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Import .json"), b -> {
                status = "Kopiere eine Profil-JSON nach config/keybind-profiles/exports und starte Import über die Dateiliste.";
            }).bounds(left + 90, this.height - 62, 100, 20).build());

            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Schließen"), b -> onClose())
                    .bounds(left + 195, this.height - 62, 75, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            renderBackground(graphics, mouseX, mouseY, delta);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 30, 0xAAAAAA);
            graphics.drawString(this.font, "Profile werden in config/keybind-profiles gespeichert.", this.width / 2 - 150, 25, 0xBBBBBB);
            super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().gui.setScreen(parent);
        }
    }
}
