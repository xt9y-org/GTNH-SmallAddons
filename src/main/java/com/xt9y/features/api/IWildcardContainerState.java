package com.xt9y.features.api;

import net.minecraft.entity.player.EntityPlayer;

/** Client-readable AE2 interface wildcard state, synchronized by the container. */
public interface IWildcardContainerState {

    boolean xt9y$isWildcardEnabled();

    void xt9y$toggleWildcard(EntityPlayer player);
}
