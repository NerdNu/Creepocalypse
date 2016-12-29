package nu.nerd.creepocalypse;

import org.bukkit.configuration.file.FileConfiguration;

// --------------------------------------------------------------------------
/**
 * Encapsulates configuration access.
 */
public class Configuration {
    /**
     * Scaling factor multiplied into the blast radius of creeper explosions.
     * 
     * By setting this < 1.0, we can mitigate the difficulty of so many
     * explosions, somewhat.
     */
    public double BLAST_RADIUS_SCALE;

    /**
     * Chance, [0.0, 1.0], of a creeper being spawned as a charged creeper.
     */
    public double CHARGED_CHANCE;

    /**
     * Minimum number of reinforcements spawned on creeper detonation.
     */
    public int MIN_REINFORCEMENTS;

    /**
     * Maximum number of reinforcements spawned on creeper detonation.
     */
    public int MAX_REINFORCEMENTS;

    /**
     * Range (distance) from exploding creeper at which reinforcements spawn.
     */
    public double REINFORCEMENT_RANGE;

    /**
     * Minimum reinforcement launch speed.
     */
    public double MIN_REINFORCEMENT_SPEED;

    /**
     * Maximum reinforcement launch speed.
     */
    public double MAX_REINFORCEMENT_SPEED;

    /**
     * Chance, [0.0, 1.0], of dropping a firework when slain by a player.
     */
    public double FIREWORK_DROP_CHANCE;

    /**
     * Minimum size of dropped firework item stack when creeper slain.
     */
    public int MIN_FIREWORK_DROPS;

    /**
     * Maximum size of dropped firework item stack when creeper slain.
     */
    public int MAX_FIREWORK_DROPS;

    /**
     * Chance, [0.0, 1.0], of a charged creeper dropping a skull when slain by a
     * player.
     */
    public double CHARGED_CREEPER_SKULL_CHANCE;

    // ------------------------------------------------------------------------
    /**
     * Load the configuration.
     */
    public void reload() {
        Creepocalypse.PLUGIN.reloadConfig();
        FileConfiguration config = Creepocalypse.PLUGIN.getConfig();

        BLAST_RADIUS_SCALE = config.getDouble("difficulty.blastscale");
        CHARGED_CHANCE = config.getDouble("difficulty.charged");
        MIN_REINFORCEMENTS = config.getInt("reinforcements.min");
        MAX_REINFORCEMENTS = config.getInt("reinforcements.max");
        REINFORCEMENT_RANGE = config.getDouble("reinforcements.range");
        MIN_REINFORCEMENT_SPEED = config.getDouble("reinforcements.velocity.min");
        MAX_REINFORCEMENT_SPEED = config.getDouble("reinforcements.velocity.max");
        FIREWORK_DROP_CHANCE = config.getDouble("drops.firework.chance");
        MIN_FIREWORK_DROPS = config.getInt("drops.firework.min");
        MAX_FIREWORK_DROPS = config.getInt("drops.firework.max");
        CHARGED_CREEPER_SKULL_CHANCE = config.getDouble("drops.skull.chance");
    }
} // class Configuration