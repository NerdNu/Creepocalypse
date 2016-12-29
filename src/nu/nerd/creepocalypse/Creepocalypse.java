package nu.nerd.creepocalypse;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
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
     * This plugin as a Singleton.
     */
    public static Creepocalypse PLUGIN;

    /**
     * Configuration as a Singleton.
     */
    public static Configuration CONFIG = new Configuration();

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        PLUGIN = this;
        SPECIAL_META = new FixedMetadataValue(this, null);

        saveDefaultConfig();
        CONFIG.reload();
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
                CONFIG.reload();
                sender.sendMessage(ChatColor.GOLD + "Creepocalypse configuration reloaded.");
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * Replace naturally spawned monsters in the affected world with creepers.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!CONFIG.isAffectedWorld(event) ||
            event.getSpawnReason() != SpawnReason.NATURAL ||
            !isEligibleHostileMob(event.getEntityType())) {
            return;
        }

        Entity originalMob = event.getEntity();
        Creeper creeper;
        if (event.getEntityType() == EntityType.CREEPER) {
            creeper = (Creeper) originalMob;
        } else {
            Location loc = originalMob.getLocation();
            creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
            originalMob.remove();
        }
        creeper.setMetadata(SPECIAL_KEY, SPECIAL_META);

        if (Math.random() < CONFIG.CHARGED_CHANCE) {
            creeper.setPowered(true);
        }
    } // onCreatureSpawn

    // ------------------------------------------------------------------------
    /**
     * Tag creepers hurt by players.
     * 
     * Only those creepers hurt recently by players will have special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreeperDamage(EntityDamageByEntityEvent event) {
        if (!CONFIG.isAffectedWorld(event)) {
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
                creeper.setMetadata(PLAYER_DAMAGE_TIME_KEY,
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
        if (!CONFIG.isAffectedWorld(event)) {
            return;
        }

        if (event.getEntityType() == EntityType.CREEPER) {
            event.setRadius((float) CONFIG.BLAST_RADIUS_SCALE * event.getRadius());

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
        if (!CONFIG.isAffectedWorld(event)) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.CREEPER && entity.hasMetadata(SPECIAL_KEY)) {
            Creeper creeper = (Creeper) entity;

            // Require recent player damage on the creeper for special drops.
            Long damageTime = getPlayerDamageTime(entity);
            if (damageTime != null) {
                Location loc = creeper.getLocation();
                if (loc.getWorld().getFullTime() - damageTime < PLAYER_DAMAGE_TICKS) {
                    if (Math.random() < CONFIG.FIREWORK_DROP_CHANCE) {
                        // Replace the default drops.
                        event.getDrops().clear();
                        final int amount = random(CONFIG.MIN_FIREWORK_DROPS, CONFIG.MAX_FIREWORK_DROPS);
                        for (int i = 0; i < amount; ++i) {
                            ItemStack firework = new ItemStack(Material.FIREWORK);
                            FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
                            meta.setPower(random(0, 3));
                            meta.addEffect(randomFireworkFffect(false));
                            firework.setItemMeta(meta);
                            event.getDrops().add(firework);
                        }
                    }

                    // Powered creepers may drop a creeper skull in addition to
                    // fireworks.
                    if (creeper.isPowered() && Math.random() < CONFIG.CHARGED_CREEPER_SKULL_CHANCE) {
                        event.getDrops().add(new ItemStack(Material.SKULL_ITEM, 1, (short) 4));
                    }
                }
            }
        }
    } // onCreeperDeath

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity type is that of a hostile mob that is
     * eligible to be replaced with a creeper.
     *
     * @param type the entity's type.
     * @return true if the specified entity type is that of a hostile mob that
     *         is eligible to be replaced with a creeper
     */
    protected boolean isEligibleHostileMob(EntityType type) {
        return type == EntityType.CREEPER ||
               type == EntityType.SPIDER ||
               type == EntityType.SKELETON ||
               type == EntityType.ZOMBIE ||
               type == EntityType.ENDERMAN ||
               type == EntityType.WITCH;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the world time when a player damaged the specified entity, if
     * stored as a PLAYER_DAMAGE_TIME_KEY metadata value, or null if that didn't
     * happen.
     *
     * @param entity the entity (mob).
     * @return the damage time stamp as Long, or null.
     */
    protected Long getPlayerDamageTime(Entity entity) {
        List<MetadataValue> playerDamageTime = entity.getMetadata(PLAYER_DAMAGE_TIME_KEY);
        if (playerDamageTime.size() > 0) {
            MetadataValue value = playerDamageTime.get(0);
            if (value.value() instanceof Long) {
                return (Long) value.value();
            }
        }
        return null;
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
        final int numReinforcements = random(CONFIG.MIN_REINFORCEMENTS, CONFIG.MAX_REINFORCEMENTS);
        for (int i = 0; i < numReinforcements; ++i) {
            // Compute unit velocity vector components, given 45 degree pitch.
            double yaw = 2.0 * Math.PI * Math.random();
            double y = INV_ROOT_2;
            double x = INV_ROOT_2 * Math.cos(yaw);
            double z = INV_ROOT_2 * Math.sin(yaw);

            // Spawn one reinforcement.
            Location origin = creeper.getLocation();
            World world = origin.getWorld();
            Location loc = origin.clone().add(CONFIG.REINFORCEMENT_RANGE * x, 0.5, CONFIG.REINFORCEMENT_RANGE * z);
            Creeper reinforcement = (Creeper) world.spawnEntity(loc, EntityType.CREEPER);
            if (reinforcement != null) {
                reinforcement.setMetadata(SPECIAL_KEY, SPECIAL_META);

                double speed = random(CONFIG.MIN_REINFORCEMENT_SPEED, CONFIG.MAX_REINFORCEMENT_SPEED);
                Vector velocity = new Vector(speed * x, speed * y, speed * z);
                reinforcement.setVelocity(velocity);

                // Randomly charge a fraction of creepers.
                if (Math.random() < CONFIG.CHARGED_CHANCE) {
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
     * Metadata key for SPECIAL_META.
     */
    protected static final String SPECIAL_KEY = "Creepocalypse_Special";

    /**
     * Shared metadata value for creepers that drop special drops.
     */
    protected static FixedMetadataValue SPECIAL_META;

    /**
     * Metadata key used for metadata stored on creepers to record last damage
     * time by a player.
     */
    protected static final String PLAYER_DAMAGE_TIME_KEY = "Creepocalypse_PlayerDamageTime";

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

} // class Creepocalypse
