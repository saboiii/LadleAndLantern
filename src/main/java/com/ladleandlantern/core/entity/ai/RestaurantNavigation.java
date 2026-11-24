package com.ladleandlantern.core.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

public class RestaurantNavigation extends GroundPathNavigation {
    public RestaurantNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setCanPassDoors(true);
    }

    public double lastSpeed() {
        return speedModifier > 0 ? speedModifier : 0.6;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new RestaurantNodeEvaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }
}
