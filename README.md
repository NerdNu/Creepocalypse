Creepocalypse
=============

Creepocalypse replaces all overworld mobs with a mixture of ordinary and charged creepers.  They drop fireworks and skulls and set off fireworks when they explode.

In what follows, **emphasised** figures are customisable in the configuration file.
 
 
Spawning
--------

 * Only overworld mob spawns are affected.
 * All mobs are replaced with creepers, except where spawned by a mob spawner or within 15 blocks of a mob spawner.
 * **20%** of spawned creepers are charged creepers.
 * If a creeper is allowed to detonate, it will spawn **0 to 5** reinforcement creepers in random directions around the explosion, that are then flung away by the blast.
 * Detonation also sets off a firework of power 0 or 1 (low) that can include the option of a creeper-shaped explosion (all firework types equally).
 
 
Drops
-----

 * Custom drops are only given if the player damaged the mob directly (using a fist, bow, sword etc) within the last 5 seconds.
 * The Looting enchant has no effect.
 * Reinforcements spawned from creepers that spawned by or near a mob spawner are prevented from giving custom drops.
 * **30%** of player-killed creeper drops upon death are replaced with **0 to 2** custom fireworks of power 0 to 3.  These do not include creeper-shaped firework types, giving players a reason to use up their santa heads etc from Christmas.
 * **3%** of player-killed charged creepers will drop a creeper skull.  This is slightly less than that for wither skulls with Looting 3, but charged creepers will be more prevalent than withers are in fortresses.
 * I have not added any other drops at the moment.  Those could be added with OtherDrops. I'd prefer to keep this plugin relatively simple.


Difficulty
----------

 * The relative prevalance of charged creepers has a marked effect on difficulty.
 * The blast radius of all creeper explosions is scaled by a factor of **0.6**, which makes the plugin more survivable.   Difficulty has been tuned to a reasonable level for iron armour.
 * Allowing creepers to detonate will potentially result in a charged creeper being blasted to a position behind the player, which can be very dangerous.
 * With good quality diamond armour you can be a bit cavalier about even the charged creepers, allowing them to blast you into the air.  That can be fun!
 
 
