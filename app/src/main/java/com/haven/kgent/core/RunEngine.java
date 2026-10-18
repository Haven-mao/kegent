package com.haven.kgent.core;

/** Timing is monotonic; the service supplies elapsedRealtime(), never wall-clock time. */
public final class RunEngine {
    public enum State {
        READY,
        RUNNING,
        PAUSED,
        STOPPED,
        FINISHED,
        ERROR
    }

    public static final class Snapshot {
        public final State state;
        public final double distance, targetDistance, speed;
        public final long elapsedMillis;

        public Snapshot(
                State state,
                double distance,
                double targetDistance,
                double speed,
                long elapsedMillis) {
            this.state = state;
            this.distance = distance;
            this.targetDistance = targetDistance;
            this.speed = speed;
            this.elapsedMillis = elapsedMillis;
        }

        public boolean active() {
            return state == State.RUNNING || state == State.PAUSED;
        }
    }

    private State state = State.READY;
    private double distance, target, speed, elapsed;
    private long lastTick;

    public synchronized void start(double targetMeters, double metersPerSecond, long now) {
        requireSpeed(metersPerSecond);
        if (!Double.isFinite(targetMeters) || targetMeters < 1 || targetMeters > 200000)
            throw new IllegalArgumentException("目标距离应在 1 米至 200 公里之间");
        if (snapshot().active()) throw new IllegalStateException("请先结束当前运行");
        distance = elapsed = 0;
        target = targetMeters;
        speed = metersPerSecond;
        lastTick = now;
        state = State.RUNNING;
    }

    public synchronized Snapshot tick(long now) {
        if (state == State.RUNNING) {
            double delta = Math.max(0, now - lastTick);
            double consumed = Math.min(delta, (target - distance) / speed * 1000);
            distance = Math.min(target, distance + consumed * speed / 1000);
            elapsed += consumed;
            if (distance >= target - 0.00001) state = State.FINISHED;
        }
        lastTick = Math.max(now, lastTick);
        return snapshot();
    }

    public synchronized void pause(long now) {
        tick(now);
        if (state == State.RUNNING) state = State.PAUSED;
    }

    public synchronized void resume(long now) {
        if (state == State.PAUSED) {
            lastTick = now;
            state = State.RUNNING;
        }
    }

    public synchronized void updateSpeed(double value, long now) {
        requireSpeed(value);
        tick(now);
        speed = value;
    }

    public synchronized void stop(long now) {
        tick(now);
        if (state != State.FINISHED) state = State.STOPPED;
    }

    public synchronized void fail() {
        state = State.ERROR;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(state, distance, target, speed, Math.round(elapsed));
    }

    private static void requireSpeed(double speed) {
        if (!Double.isFinite(speed) || speed < 0.1 || speed > 30)
            throw new IllegalArgumentException("速度应在 0.1 至 30 m/s 之间");
    }
}
