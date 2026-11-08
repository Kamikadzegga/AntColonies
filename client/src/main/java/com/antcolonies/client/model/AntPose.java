package com.antcolonies.client.model;

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
        return this;
    }
}
