package dev.lunartiers.tagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lunartiers.tagger.core.Ranks;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Properties;

public final class Settings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lunartiers-tagger.json");
    public boolean enabled = true, nametags = true, tab = true, icons = true, separator = true;
    public String mode = "auto", apiUrl = "";
    public static Settings load() {
        Settings settings = null;
        try {if (Files.exists(FILE)) settings = JSON.fromJson(Files.readString(FILE), Settings.class);} catch (Exception error) {LunarTagger.LOGGER.warn("Could not read Lunar settings; using defaults");}
        if (settings == null) settings = new Settings();
        if (settings.mode == null || (!"auto".equals(settings.mode) && !Ranks.MODES.contains(settings.mode))) settings.mode = "auto";
        if (settings.apiUrl == null) settings.apiUrl = "";
        if (settings.apiUrl.isBlank()) {
            Properties defaults = new Properties();
            try (var stream = Settings.class.getResourceAsStream("/lunar-endpoint.properties")) {if (stream != null) defaults.load(stream); settings.apiUrl = defaults.getProperty("url", "");} catch (Exception ignored) {}
        }
        return settings;
    }
    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = Files.createTempFile(FILE.getParent(), "lunartiers-", ".tmp");
            try {Files.writeString(temp, JSON.toJson(this)); try {Files.move(temp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);} catch (AtomicMoveNotSupportedException ignored) {Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);}} finally {Files.deleteIfExists(temp);}
        } catch (Exception error) {LunarTagger.LOGGER.warn("Could not save Lunar settings");}
    }
}
