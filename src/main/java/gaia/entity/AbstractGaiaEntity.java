package gaia.entity;

import gaia.capability.CapabilityHandler;
import gaia.capability.friended.IFriended;
import gaia.config.GaiaConfig;
import gaia.entity.type.IAssistMob;
import gaia.entity.type.IDayMob;
import gaia.registry.GaiaRegistry;
import gaia.util.SharedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class AbstractGaiaEntity extends Monster {
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AbstractGaiaEntity.class, EntityDataSerializers.INT);
	protected Goal targetPlayerGoal;
	protected final Goal targetMobGoal = new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false, (livingEntity) -> {
		return livingEntity instanceof Enemy && !(livingEntity instanceof Creeper);
	});

	public AbstractGaiaEntity(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.xpReward = SharedEntityData.EXPERIENCE_VALUE_1;
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(VARIANT, 0);
	}

	public int getGaiaLevel() {
		return 1;
	}

	public int getVariant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int index) {
		this.entityData.set(VARIANT, Mth.clamp(index, 0, maxVariants()));
	}

	public abstract int maxVariants();

	public abstract float getBaseDefense();

	protected float getBaseDamage(DamageSource source, float damage) {
		return source == DamageSource.OUT_OF_WORLD ? damage : Math.min(damage, getBaseDefense());
	}

	protected void spawnLingeringCloud(List<MobEffectInstance> effectInstances) {
		if (!effectInstances.isEmpty()) {
			AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level, this.getX(), this.getY(), this.getZ());
			areaeffectcloud.setRadius(2.5F);
			areaeffectcloud.setRadiusOnUse(-0.5F);
			areaeffectcloud.setWaitTime(10);
			areaeffectcloud.setDuration(areaeffectcloud.getDuration() / 2);
			areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float) areaeffectcloud.getDuration());

			for (MobEffectInstance mobeffectinstance : effectInstances) {
				areaeffectcloud.addEffect(new MobEffectInstance(mobeffectinstance));
			}

			this.level.addFreshEntity(areaeffectcloud);
		}
	}

	protected GaiaHorse createHorse(DifficultyInstance difficulty) {
		Entity entity = GaiaRegistry.HORSE.getEntityType().create(level);
		if (entity instanceof GaiaHorse horse) {
			horse.finalizeSpawn((ServerLevel) level, difficulty, null, (SpawnGroupData) null, (CompoundTag) null);
			horse.setPos(this.getX(), this.getY(), this.getZ());
			horse.setTamed(true);
			horse.setAge(0);
			level.addFreshEntity(horse);
			return horse;
		}
		return null;
	}

	protected static boolean isHalloween() {
		LocalDate localdate = LocalDate.now();
		int i = localdate.get(ChronoField.DAY_OF_MONTH);
		int j = localdate.get(ChronoField.MONTH_OF_YEAR);
		return j == 10 && i >= 20 || j == 11 && i <= 3;
	}

	/**
	 * Detects if there are any Player nearby
	 */
	protected boolean playerDetection(int range, TargetingConditions conditions) {
		AABB box = new AABB(getX(), getY(), getZ(), getX() + 1, getY() + 1, getZ() + 1).inflate(range);
		List<Player> list = level.getNearbyPlayers(conditions, this, box);

		return !list.isEmpty();
	}

	/**
	 * Gets nearby entities and applies the given consumer to them
	 *
	 * @param range  The range to search for entities
	 * @param action The action to apply to the entities
	 */
	protected void beaconMonster(int range, Consumer<LivingEntity> action) {
		if (!level.isClientSide) {
			AABB aabb = (new AABB(getX(), getY(), getZ(), getX() + 1, getY() + 1, getZ() + 1)).inflate(range);
			List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb);
			for (LivingEntity livingEntity : entities) {
				action.accept(livingEntity);
			}
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Variant", getVariant());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Variant")) {
			int variant = tag.getInt("Variant");
			setVariant(variant);
		}
		setupFriendGoals(isFriendly());
	}

	/**
	 * @return true if this entity is a friend of the player
	 */
	public boolean isFriendly() {
		return this.getCapability(CapabilityHandler.CAPABILITY_FRIENDED).map(cap -> cap.isFriendly()).orElse(false);
	}

	/**
	 * Sets the entity to friendly or not friendly
	 *
	 * @param value      true for friendly, false for not friendly
	 * @param friendedBy the player who set the entity to friendly
	 */
	public void setFriendly(boolean value, UUID friendedBy) {
		this.getCapability(CapabilityHandler.CAPABILITY_FRIENDED).ifPresent(cap -> {
			cap.setFriendly(value);
			cap.setFriendedBy(friendedBy);
			setupFriendGoals(value);
			if (GaiaConfig.COMMON.friendlyPersistence.get()) {
				setPersistenceRequired();
			}
		});
	}

	/**
	 * Adjusts AI goals based on if the entity is friendly or not
	 *
	 * @param friendly true if the entity is friendly, false if not
	 */
	private void setupFriendGoals(boolean friendly) {
		switch (getGaiaLevel()) {
			case 1 -> {
				//Iron Golem AI
				if (friendly) {
					this.targetSelector.removeGoal(this.targetPlayerGoal);
					this.targetSelector.addGoal(2, this.targetMobGoal);
				} else {
					this.targetSelector.removeGoal(this.targetMobGoal);
					if (this instanceof IAssistMob) {
						if (GaiaConfig.COMMON.allPassiveMobsHostile.get()) {
							this.targetSelector.addGoal(2, this.targetPlayerGoal = new NearestAttackableTargetGoal<>(this, Player.class, true));
						}
					} else {
						this.targetSelector.addGoal(2, this.targetPlayerGoal = new NearestAttackableTargetGoal<>(this, Player.class, true));
					}
				}
			}
			case 2 -> {
				//Just don't target players actively
				if (friendly) {
					this.targetSelector.removeGoal(this.targetPlayerGoal);
				} else {
					if (this instanceof IAssistMob) {
						if (GaiaConfig.COMMON.allPassiveMobsHostile.get()) {
							this.targetSelector.addGoal(2, this.targetPlayerGoal = new NearestAttackableTargetGoal<>(this, Player.class, true));
						}
					} else {
						this.targetSelector.addGoal(2, this.targetPlayerGoal = new NearestAttackableTargetGoal<>(this, Player.class, true));
					}
				}
			}
		}
	}

	@Override
	public void aiStep() {
		if (getCapability(CapabilityHandler.CAPABILITY_FRIENDED).isPresent()) {
			IFriended cap = getCapability(CapabilityHandler.CAPABILITY_FRIENDED).orElse(null);
			if (cap != null) {
				if (cap.isChanged()) {
					onFriendlyChange(cap);
					cap.setChanged(false);
				}
			}
		}

		super.aiStep();
	}

	/**
	 * Called when the friendly status of the entity changes
	 *
	 * @param cap the capability
	 */
	public void onFriendlyChange(IFriended cap) {

	}

	public double getMyRidingOffset() {
		return -0.6D;
	}

	public void rideTick() {
		super.rideTick();
		if (this.getVehicle() instanceof PathfinderMob pathfindermob) {
			this.yBodyRot = pathfindermob.yBodyRot;
		}
	}

	@Override
	public boolean canAttackType(EntityType<?> type) {
		if (this instanceof IAssistMob) {
			return type != getType() && (type != EntityType.CREEPER && super.canAttackType(type));
		}
		return super.canAttackType(type);
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader levelReader) {
		return this instanceof IDayMob ? 0.0F : super.getWalkTargetValue(pos, levelReader);
	}

	public static boolean checkGaiaDaySpawnRules(EntityType<? extends Monster> entityType, ServerLevelAccessor levelAccessor, MobSpawnType spawnType, BlockPos pos, Random random) {
		return checkDaylight(levelAccessor, pos) && checkAnyLightMonsterSpawnRules(entityType, levelAccessor, spawnType, pos, random);
	}

	/**
	 * Check if the lighting is good for spawning
	 */
	protected static boolean checkDaylight(ServerLevelAccessor levelAccessor, BlockPos pos) {
		return levelAccessor.canSeeSky(pos) && levelAccessor.getBrightness(LightLayer.BLOCK, pos) == 0;
	}

	/**
	 * Checks if the position is above sea level so the entity can spawn
	 */
	protected static boolean checkAboveSeaLevel(ServerLevelAccessor levelAccessor, BlockPos pos) {
		return GaiaConfig.COMMON.disableYRestriction.get() || pos.getY() > levelAccessor.getSeaLevel() - 16;
	}

	/**
	 * Checks if the position is below sea level so the entity can spawn
	 */
	protected static boolean checkBelowSeaLevel(ServerLevelAccessor levelAccessor, BlockPos pos) {
		return GaiaConfig.COMMON.disableYRestriction.get() || pos.getY() < levelAccessor.getSeaLevel() - 16;
	}

	/**
	 * Checks if the block under the spawn location is in the given tag so the entity can spawn
	 */
	protected static boolean checkTagBlocks(ServerLevelAccessor levelAccessor, BlockPos pos, TagKey<Block> blockTag) {
		return levelAccessor.getBlockState(pos.below()).is(blockTag);
	}

	/**
	 * Checks if it's day time so the entity can spawn
	 */
	protected static boolean checkDaytime(ServerLevelAccessor levelAccessor) {
		return !levelAccessor.dimensionType().hasFixedTime() && levelAccessor.getSkyDarken() < 4;
	}

	/**
	 * Checks if it's raining so the entity can spawn
	 */
	protected static boolean checkRaining(ServerLevelAccessor levelAccessor) {
		return GaiaConfig.COMMON.spawnWeather.get() || levelAccessor.getLevelData().isRaining();
	}

	/**
	 * Checks if the configured amount of days have passed so the entity can spawn
	 */
	protected static boolean checkDaysPassed(ServerLevelAccessor levelAccessor) {
		if (GaiaConfig.COMMON.spawnDaysPassed.get()) {
			return GaiaConfig.COMMON.spawnDaysSet.get() <= (int) (levelAccessor.dayTime() % 24000L);
		} else {
			return true;
		}
	}
}