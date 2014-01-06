package nu.nerd.creepocalypse;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

// ----------------------------------------------------------------------------
/**
 * Main plugin class.
 */
public class Creepocalypse extends JavaPlugin implements Listener {
    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        if (FROM_SPAWNER_META == null) {
            FROM_SPAWNER_META = new FixedMetadataValue(this, null);
        }

        saveDefaultConfig();
        loadConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
    }

    // ------------------------------------------------------------------------
    /**
     * Handle commands.
     * 
     * <ul>
     * <li>/creepocalypse reload</li>
     * </ul>
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
        if (command.getName().equalsIgnoreCase("creepocalypse")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                loadConfiguration();
                sender.sendMessage(ChatColor.GOLD + "Creepocalypse configuration reloaded.");
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * Replace non-creepers with creepers, except near dungeons.
     * 
     * Tag creepers spawned in spawners so we can suppress special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Only applies to overworld.
        Entity entity = event.getEntity();
        if (entity.getWorld() != Bukkit.getWorlds().get(0)) {
            return;
        }

        Creeper creeper = null;
        if (event.getEntityType() == EntityType.CREEPER) {
            creeper = (Creeper) entity;
            if (event.getSpawnReason() == SpawnReason.SPAWNER || isSpawnerNear(creeper.getLocation(), 15)) {
                tagSpawnerCreeper(creeper);
            }
        } else {
            // Replace naturally spawned hostile creatures with a creeper.
            // This spawn can probably cause re-entry of this event handler.
            if (event.getSpawnReason() == SpawnReason.NATURAL && entity instanceof Monster) {
                Location loc = entity.getLocation();
                creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);

                // Remove original spawn.
                Monster monster = (Monster) entity;
                monster.remove();
            }
        }

        // Randomly charge a fraction of creepers.
        if (creeper != null && Math.random() < _chargedChance) {
            creeper.setPowered(true);
        }
    } // onCreatureSpawn

    // ------------------------------------------------------------------------
    /**
     * Tag creepers spawned in spawners so we can suppress special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreeperDamage(EntityDamageByEntityEvent event) {
        // Only applies to overworld.
        if (event.getEntity().getWorld() != Bukkit.getWorlds().get(0)) {
            return;
        }

        if (event.getEntityType() == EntityType.CREEPER) {
            boolean isPlayerAttack = false;
            if (event.getDamager() instanceof Player) {
                isPlayerAttack = true;
            } else if (event.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) event.getDamager();
                if (projectile.getShooter() instanceof Player) {
                    isPlayerAttack = true;
                }
            }

            // Tag creepers hurt by players with the damage time stamp.
            if (isPlayerAttack) {
                Entity creeper = event.getEntity();
                creeper.setMetadata(PLAYER_DAMAGE_TIME,
                    new FixedMetadataValue(this, new Long(creeper.getWorld().getFullTime())));
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Event handler called when an explosive is primed.
     * 
     * We use it to detect impending creeper explosions. The event is fired
     * immediately before the explosion.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreeperDetonate(ExplosionPrimeEvent event) {
        // Only applies to overworld.
        if (event.getEntity().getWorld() != Bukkit.getWorlds().get(0)) {
            return;
        }

        if (event.getEntityType() == EntityType.CREEPER) {
            // Apply scaling factor to blast radius.
            event.setRadius((float) _blastRadiusScale * event.getRadius());

            Entity creeper = event.getEntity();
            launchReinforcements(creeper);

            Location origin = creeper.getLocation();
            World world = origin.getWorld();
            Firework firework = (Firework) world.spawnEntity(origin, EntityType.FIREWORK);
            if (firework != null) {
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(random(0, 1));
                meta.addEffect(randomFireworkFffect(true));
                firework.setFireworkMeta(meta);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * On creeper death, drop fireworks and heads with a configurable chance.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreeperDeath(EntityDeathEvent event) {
        // Only applies to overworld.
        if (event.getEntity().getWorld() != Bukkit.getWorlds().get(0)) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.CREEPER && !entity.hasMetadata(FROM_SPAWNER)) {
            Creeper creeper = (Creeper) entity;

            // Require recent player damage on the creeper for firework drops.
            List<MetadataValue> playerDamageTime = creeper.getMetadata(PLAYER_DAMAGE_TIME);
            if (playerDamageTime.size() != 0) {
                MetadataValue value = playerDamageTime.get(0);
                if (value.value() instanceof Long) {
                    Location loc = creeper.getLocation();
                    World world = loc.getWorld();
                    if (world.getFullTime() - value.asLong() < PLAYER_DAMAGE_TICKS &&
                        !isSpawnerNear(loc, 15)) {
                        if (Math.random() < _fireworkDropChance) {
                            // Replace the default drops.
                            event.getDrops().clear();
                            final int amount = random(_minFireworkDrops, _maxFireworkDrops);
                            for (int i = 0; i < amount; ++i) {
                                ItemStack firework = new ItemStack(Material.FIREWORK);
                                FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
                                meta.setPower(random(0, 3));
                                meta.addEffect(randomFireworkFffect(false));
                                firework.setItemMeta(meta);
                                event.getDrops().add(firework);
                            }
                        }

                        // Powered creepers may drop a skull:4 in addition to
                        // fireworks.
                        if (creeper.isPowered() && Math.random() < _chargedCreeperSkullDropChance) {
                            event.getDrops().add(new ItemStack(Material.SKULL_ITEM, 1, (short) 4));
                        }
                    }
                }
            }
        }
    } // onCreeperDeath

    // ------------------------------------------------------------------------
    /**
     * Load the configuration.
     */
    public void loadConfiguration() {
        reloadConfig();
        _blastRadiusScale = getConfig().getDouble("difficulty.blastscale", 0.6);
        _chargedChance = getConfig().getDouble("difficulty.charged", 0.2);
        _minReinforcements = getConfig().getInt("reinforcements.min", 0);
        _maxReinforcements = getConfig().getInt("reinforcements.max", 5);
        _reinforcementRange = getConfig().getDouble("reinforcements.range", 6.0);
        _minReinforcementSpeed = getConfig().getDouble("reinforcements.velocity.min", 1);
        _maxReinforcementSpeed = getConfig().getDouble("reinforcements.velocity.max", 3);
        _fireworkDropChance = getConfig().getDouble("drops.firework.chance", 0.3);
        _minFireworkDrops = getConfig().getInt("drops.firework.min", 0);
        _maxFireworkDrops = getConfig().getInt("drops.firework.max", 2);
        _chargedCreeperSkullDropChance = getConfig().getDouble("drops.skull.chance", 0.03);
    }

    // ------------------------------------------------------------------------
    /**
     * When a creeper explodes, reinforcements are launched from him with random
     * velocities by this method.
     * 
     * Reinforcements are always launched at a 45 degree angle, a configurable
     * range from the exploding creeper.
     * 
     * @param creeper the exploding creeper.
     */
    protected void launchReinforcements(Entity creeper) {
        final int numReinforcements = random(_minReinforcements, _maxReinforcements);
        for (int i = 0; i < numReinforcements; ++i) {
            // Compute unit velocity vector components, given 45 degree pitch.
            double yaw = 2.0 * Math.PI * Math.random();
            double y = INV_ROOT_2;
            double x = INV_ROOT_2 * Math.cos(yaw);
            double z = INV_ROOT_2 * Math.sin(yaw);

            // Spawn one reinforcement.
            Location origin = creeper.getLocation();
            World world = origin.getWorld();
            Location loc = origin.clone().add(_reinforcementRange * x, 0.5, _reinforcementRange * z);
            Creeper reinforcement = (Creeper) world.spawnEntity(loc, EntityType.CREEPER);
            if (reinforcement != null) {
                // Add spawner metadata tag if the original creeper came from
                // a spawner.
                if (creeper.hasMetadata(FROM_SPAWNER)) {
                    tagSpawnerCreeper(reinforcement);
                }

                double speed = random(_minReinforcementSpeed, _maxReinforcementSpeed);
                Vector velocity = new Vector(speed * x, speed * y, speed * z);
                reinforcement.setVelocity(velocity);

                // Randomly charge a fraction of creepers.
                if (Math.random() < _chargedChance) {
                    reinforcement.setPowered(true);
                }
            }
        }
    } // launchReinforcements

    // ------------------------------------------------------------------------
    /**
     * Return a random firework effect.
     * 
     * @param boolean allowCreeperType if true, creeper shaped firework
     *        explosion types are allowed.
     * @return a FireworkEffect instance.
     */
    protected FireworkEffect randomFireworkFffect(boolean allowCreeperType) {
        FireworkEffect.Builder builder = FireworkEffect.builder();
        if (Math.random() < 0.3) {
            builder.withFlicker();
        }
        if (Math.random() < 0.3) {
            builder.withTrail();
        }

        final FireworkEffect.Type[] TYPES = allowCreeperType ? FireworkEffect.Type.values()
                                                            : NON_CREEPER_FIREWORK_TYPES;
        builder.with(TYPES[random(0, TYPES.length - 1)]);

        final int primaryColors = random(1, 4);
        for (int i = 0; i < primaryColors; ++i) {
            builder.withColor(Color.fromRGB(random(0, 255), random(0, 255), random(0, 255)));
        }

        final int fadeColors = random(1, 4);
        for (int i = 0; i < fadeColors; ++i) {
            builder.withFade(Color.fromRGB(random(0, 255), random(0, 255), random(0, 255)));
        }

        return builder.build();
    }

    // ------------------------------------------------------------------------
    /**
     * Tag a creeper originating from a spawner with metadata so that we can
     * suppress special drops later.
     * 
     * Any reinforcements spawned from the detonation of a tagged creeper get
     * the same tag, so they will also not drop special drops.
     * 
     * @param creeper the creeper.
     */
    protected void tagSpawnerCreeper(Entity creeper) {
        creeper.setMetadata(FROM_SPAWNER, FROM_SPAWNER_META);
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if there is a mob spawner in the cube of the specified
     * "radius" around the Location.
     * 
     * @return true if a mob spawner is within the cube.
     */
    protected static boolean isSpawnerNear(Location loc, int radius) {
        if (radius != 0 && loc != null) {
            World world = loc.getWorld();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dy = -radius; dy <= radius; ++dy) {
                    for (int dz = -radius; dz <= radius; ++dz) {
                        Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (block.getType() == Material.MOB_SPAWNER) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Return a random integer in the range [min,max].
     * 
     * @return a random integer in the range [min,max].
     */
    protected int random(int min, int max) {
        return min + (int) Math.rint(Math.random() * (max - min));
    }

    // ------------------------------------------------------------------------
    /**
     * Return a random double in the range [min,max].
     * 
     * @return a random double in the range [min,max].
     */
    protected double random(double min, double max) {
        return min + Math.random() * (max - min);
    }

    // ------------------------------------------------------------------------
    /**
     * Metadata name used to tag creepers spawned from spawners and
     * reinforcements of creepers with that metadata.
     */
    protected static final String FROM_SPAWNER = "FromSpawner";

    /**
     * Shared metadata value for all tagged creepers.
     */
    protected static FixedMetadataValue FROM_SPAWNER_META;

    /**
     * Metadata name used for metadata stored on creepers to record last damage
     * time by a player.
     */
    protected static final String PLAYER_DAMAGE_TIME = "PlayerDamageTime";

    /**
     * Array of all firework types except the creeper-head-shaped type.
     */
    protected static FireworkEffect.Type NON_CREEPER_FIREWORK_TYPES[];
    static {
        NON_CREEPER_FIREWORK_TYPES = new FireworkEffect.Type[FireworkEffect.Type.values().length - 1];
        for (int i = 0; i < NON_CREEPER_FIREWORK_TYPES.length; ++i) {
            FireworkEffect.Type type = FireworkEffect.Type.values()[i];
            if (type != FireworkEffect.Type.CREEPER) {
                NON_CREEPER_FIREWORK_TYPES[i] = type;
            }
        }
    }

    /**
     * Time in ticks (1/20ths of a second) for which player attack damage
     * "sticks" to a creeper. The time between the last player damage on a
     * creeper and its death must be less than this for it to drop fireworks.
     */
    protected static final int PLAYER_DAMAGE_TICKS = 100;

    /**
     * Inverse of the square root of 2; cos()/sin() of a 45 degree angle.
     */
    protected static final double INV_ROOT_2 = 1 / Math.sqrt(2.0);

    /**
     * Scaling factor multiplied into the blast radius of creeper explosions.
     * 
     * By setting this < 1.0, we can mitigate the difficulty of so many
     * explosions, somewhat.
     */
    protected double _blastRadiusScale;

    /**
     * Chance, [0.0, 1.0], of a creeper being spawned as a charged creeper.
     */
    protected double _chargedChance;

    /**
     * Minimum number of reinforcements spawned on creeper detonation.
     */
    protected int _minReinforcements;

    /**
     * Maximum number of reinforcements spawned on creeper detonation.
     */
    protected int _maxReinforcements;

    /**
     * Range (distance) from exploding creeper at which reinforcements spawn.
     */
    protected double _reinforcementRange;

    /**
     * Minimum reinforcement launch speed.
     */
    protected double _minReinforcementSpeed;

    /**
     * Maximum reinforcement launch speed.
     */
    protected double _maxReinforcementSpeed;

    /**
     * Chance, [0.0, 1.0], of dropping a firework when slain by a player.
     */
    protected double _fireworkDropChance;

    /**
     * Minimum size of dropped firework item stack when creeper slain.
     */
    protected int _minFireworkDrops;

    /**
     * Maximum size of dropped firework item stack when creeper slain.
     */
    protected int _maxFireworkDrops;

    /**
     * Chance, [0.0, 1.0], of a charged creeper dropping a skull when slain by a
     * player.
     */
    protected double _chargedCreeperSkullDropChance;
} // class Creepocalypse
