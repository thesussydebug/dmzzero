package com.dmzzero.beam;

/** Pure maths for combined beams: where two beams meet, who leads, and what the combined beam gains. */
public final class BeamMath {

    public record V(double x, double y, double z) {
        public V add(V o) {
            return new V(x + o.x, y + o.y, z + o.z);
        }

        public V sub(V o) {
            return new V(x - o.x, y - o.y, z - o.z);
        }

        public V scale(double s) {
            return new V(x * s, y * s, z * s);
        }

        public double dot(V o) {
            return x * o.x + y * o.y + z * o.z;
        }

        public double length() {
            return Math.sqrt(dot(this));
        }

        public V normalize() {
            double len = length();
            return len < 1.0e-12 ? this : scale(1.0 / len);
        }
    }

    /** Where two beams touch: how far along each beam, the meeting point, and how far apart their axes are there. */
    public record Contact(double alongA, double alongB, V point, double distance) {
    }

    private BeamMath() {
    }

    /** The same as vanilla's Vec3.directionFromRotation(pitch, yaw). */
    public static V direction(float pitchDeg, float yawDeg) {
        double f = Math.cos(-yawDeg * (Math.PI / 180.0) - Math.PI);
        double f1 = Math.sin(-yawDeg * (Math.PI / 180.0) - Math.PI);
        double f2 = -Math.cos(-pitchDeg * (Math.PI / 180.0));
        double f3 = Math.sin(-pitchDeg * (Math.PI / 180.0));
        return new V(f1 * f2, f3, f * f2);
    }

    /** Degrees between two unit directions. */
    public static double angleDeg(V a, V b) {
        double dot = Math.max(-1.0, Math.min(1.0, a.dot(b)));
        return Math.toDegrees(Math.acos(dot));
    }

    /** How close two beams' axes have to come to touch. */
    public static double touchRadius(double sizeA, double sizeB) {
        return (Math.max(0, sizeA) + Math.max(0, sizeB)) * 0.5 + 1.0;
    }

    /**
     * Where beam A (from a0 along unit dirA, lenA long) touches beam B, at least {@code ahead} blocks out along both,
     * or null if they don't touch there.
     */
    public static Contact contact(V a0, V dirA, double lenA, double sizeA, V b0, V dirB, double lenB, double sizeB, double ahead) {
        if (lenA < ahead || lenB < ahead) return null;
        double[] st = closestOnSegments(a0, dirA, lenA, b0, dirB, lenB);
        double s = st[0], t = st[1];
        // closest behind the "ahead" mark (side by side, or spreading apart): look from the mark instead
        for (int i = 0; i < 2 && (s < ahead || t < ahead); i++) {
            s = clamp(s, ahead, lenA);
            t = clamp(a0.add(dirA.scale(s)).sub(b0).dot(dirB), ahead, lenB);
            s = clamp(b0.add(dirB.scale(t)).sub(a0).dot(dirA), ahead, lenA);
        }
        V pa = a0.add(dirA.scale(s));
        V pb = b0.add(dirB.scale(t));
        double distance = pa.sub(pb).length();
        if (distance > touchRadius(sizeA, sizeB)) return null;
        return new Contact(s, t, pa.add(pb).scale(0.5), distance);
    }

    // closest points between two segments (Ericson, Real-Time Collision Detection 5.1.9), as distances along each
    private static double[] closestOnSegments(V p1, V dA, double lenA, V p2, V dB, double lenB) {
        V d1 = dA.scale(lenA);
        V d2 = dB.scale(lenB);
        V r = p1.sub(p2);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        double c = d1.dot(r);
        double b = d1.dot(d2);
        double denom = a * e - b * b;
        s = denom > 1.0e-12 ? clamp((b * f - c * e) / denom, 0, 1) : 0;
        t = (b * s + f) / e;
        if (t < 0) {
            t = 0;
            s = clamp(-c / a, 0, 1);
        } else if (t > 1) {
            t = 1;
            s = clamp((b - c) / a, 0, 1);
        }
        return new double[]{s * lenA, t * lenB};
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static boolean firedTogether(long fireA, long fireB, int windowTicks) {
        return Math.abs(fireA - fireB) <= windowTicks;
    }

    /** The beam that carries on: the most damage, then the first fired, then the first listed. */
    public static int leader(double[] damages, long[] fireTimes) {
        int best = 0;
        for (int i = 1; i < damages.length; i++) {
            if (damages[i] > damages[best] || (damages[i] == damages[best] && fireTimes[i] < fireTimes[best])) best = i;
        }
        return best;
    }

    /** What the leader gains from its helpers' damage or size: never negative, so it never drops below its own. */
    public static double bonus(double[] helperValues, double share) {
        double sum = 0;
        for (double v : helperValues) if (v > 0 && Double.isFinite(v)) sum += v;
        double out = sum * share;
        return out > 0 && Double.isFinite(out) ? out : 0;
    }

    /** Swaps the bonus we added for a new one, keeping anything else that changed the value meanwhile. */
    public static double reapply(double current, double oldBonus, double newBonus) {
        return current - oldBonus + newBonus;
    }

    public static int blend(int from, int to, double t) {
        int r = mix((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = mix((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = mix(from & 0xFF, to & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    public static int average(int[] colours) {
        if (colours.length == 0) return 0;
        double r = 0, g = 0, b = 0;
        for (int c : colours) {
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int n = colours.length;
        return ((int) Math.round(r / n) << 16) | ((int) Math.round(g / n) << 8) | (int) Math.round(b / n);
    }
}
