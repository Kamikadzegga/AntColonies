package com.antcolonies.client.model;

import java.util.List;

import com.antcolonies.colony.species.Costume;

import org.jspecify.annotations.Nullable;

/** What an ant is doing this frame, for {@link AntModel#pose}. All values 0..1 unless noted. */
public final class AntPose {
    /** Radians of the gait cycle. */
    public float walkPhase;
    public float walkAmount;
    public float groom;
    public float rear;
    /** Radians, added to the head's rest pose. */
    public float headPitch;
    public float headYaw;
    public float gasterPulse;
    public float mandibleOpen;
    public float alert;
    /** Ticks, for idle motion (antennae, breathing). */
    public float time;
    /** Keeps ants side by side from moving their antennae in step. */
    public float seed;

    /** Hanging head and antennae (a sad ant). */
    public float droop;
    /** Units the body is off its normal height (up is negative). */
    public float bounce;
    /** The species' costume, if it has one: only that one is posed. */
    public @Nullable Costume costume;
    /** The wear parts drawn on it this frame (CostumeParts.WEAR): only those are posed. */
    public List<String> wear = List.of();

    public AntPose reset() {
        walkPhase = 0;
        walkAmount = 0;
        groom = 0;
        rear = 0;
        headPitch = 0;
        headYaw = 0;
        gasterPulse = 0;
        mandibleOpen = 0;
        alert = 0;
        time = 0;
        seed = 0;
        droop = 0;
        bounce = 0;
        costume = null;
        wear = List.of();
        return this;
    }

    /** A copy that keeps this frame's values while the renderer goes on to reuse this one for the next ant. */
    public AntPose copy() {
        AntPose copy = new AntPose();
        copy.walkPhase = walkPhase;
        copy.walkAmount = walkAmount;
        copy.groom = groom;
        copy.rear = rear;
        copy.headPitch = headPitch;
        copy.headYaw = headYaw;
        copy.gasterPulse = gasterPulse;
        copy.mandibleOpen = mandibleOpen;
        copy.alert = alert;
        copy.time = time;
        copy.seed = seed;
        copy.droop = droop;
        copy.bounce = bounce;
        copy.costume = costume;
        copy.wear = wear;
        return copy;
    }
}
