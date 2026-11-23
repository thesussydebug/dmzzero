package com.dmzzero.inspect;

import com.dmzzero.potential.Potential;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// what /dmzzero inspect says about a player, from plain facts; no minecraft state, so it can be unit tested
public final class InspectReport {
    private static final String K = "command.dmzzero.inspect.";
    static final int MAX_NAMES = 8;

    private InspectReport() {
    }

    /** One chat line: a lang key, its English text and the values for its %s slots. */
    public record Line(String key, String fallback, List<Object> args) {
        public Line {
            args = List.copyOf(args);
        }
    }

    public static Line line(String key, String fallback, Object... args) {
        return new Line(K + key, fallback, List.of(args));
    }

    public record Facts(String name, boolean online, String race, String form, int potential, boolean awakened,
                        String rival, int rivalWins, int rivalLosses, String rivalSince, boolean rivalShadow,
                        int duelWins, int duelLosses, int duelDraws, int battlesPlayed, int battlesWon, int battleKos,
                        List<String> knownKi, int firstForms, int fusions, boolean destructionOff, boolean realmReturn,
                        List<Line> now) {

        public static Builder builder(String name) {
            return new Builder(name);
        }

        public static final class Builder {
            private final String name;
            private boolean online;
            private String race = "";
            private String form = "";
            private int potential;
            private boolean awakened;
            private String rival;
            private int rivalWins;
            private int rivalLosses;
            private String rivalSince = "";
            private boolean rivalShadow;
            private int duelWins;
            private int duelLosses;
            private int duelDraws;
            private int battlesPlayed;
            private int battlesWon;
            private int battleKos;
            private List<String> knownKi = List.of();
            private int firstForms;
            private int fusions;
            private boolean destructionOff;
            private boolean realmReturn;
            private final List<Line> now = new ArrayList<>();

            private Builder(String name) {
                this.name = name;
            }

            public Builder online(String race, String form) {
                this.online = true;
                this.race = race == null ? "" : race;
                this.form = form == null ? "" : form;
                return this;
            }

            public Builder potential(int potential, boolean awakened) {
                this.potential = potential;
                this.awakened = awakened;
                return this;
            }

            public Builder rival(String name, int wins, int losses, String since, boolean shadow) {
                this.rival = name;
                this.rivalWins = wins;
                this.rivalLosses = losses;
                this.rivalSince = since == null ? "" : since;
                this.rivalShadow = shadow;
                return this;
            }

            public Builder duels(int wins, int losses, int draws) {
                this.duelWins = wins;
                this.duelLosses = losses;
                this.duelDraws = draws;
                return this;
            }

            public Builder battles(int played, int won, int kos) {
                this.battlesPlayed = played;
                this.battlesWon = won;
                this.battleKos = kos;
                return this;
            }

            public Builder knownKi(List<String> names) {
                this.knownKi = List.copyOf(names);
                return this;
            }

            public Builder firstForms(int count) {
                this.firstForms = count;
                return this;
            }

            public Builder fusions(int count) {
                this.fusions = count;
                return this;
            }

            public Builder destructionOff(boolean off) {
                this.destructionOff = off;
                return this;
            }

            public Builder realmReturn(boolean saved) {
                this.realmReturn = saved;
                return this;
            }

            public Builder now(Line line) {
                this.now.add(line);
                return this;
            }

            public Facts build() {
                return new Facts(name, online, race, form, potential, awakened, rival, rivalWins, rivalLosses, rivalSince, rivalShadow,
                        duelWins, duelLosses, duelDraws, battlesPlayed, battlesWon, battleKos, knownKi, firstForms, fusions,
                        destructionOff, realmReturn, List.copyOf(now));
            }
        }
    }

    public static List<Line> lines(Facts f) {
        List<Line> out = new ArrayList<>();
        out.add(line("header", "DMZ Zero: %s", f.name()));
        out.add(f.online()
                ? line("online", "Online. Race: %s, form: %s.", capitalize(f.race()), f.form().isEmpty() ? "base" : f.form())
                : line("offline", "Offline: showing their saved data only."));

        if (Potential.isValid(f.potential())) {
            String tp = times(Potential.tpMultiplier(f.potential()));
            String mastery = times(Potential.masteryMultiplier(f.potential()));
            out.add(f.awakened()
                    ? line("potential_awakened", "Potential %s (TP %s, mastery %s), already raised by a master.", f.potential(), tp, mastery)
                    : line("potential", "Potential %s (TP %s, mastery %s), not raised by a master yet.", f.potential(), tp, mastery));
        } else {
            out.add(line("potential_none", "Potential: not rolled yet (no character made)."));
        }

        if (f.rival() == null || f.rival().isEmpty()) {
            out.add(line("rival_none", "Rival: none."));
        } else {
            out.add(line("rival", "Rival: %s, score %s-%s, since %s.", f.rival(), f.rivalWins(), f.rivalLosses(), f.rivalSince()));
            if (f.rivalShadow()) out.add(line("rival_shadow", "A shadow of their rival is saved for training."));
        }

        out.add(line("duels", "Duels: %s won, %s lost, %s drawn.", f.duelWins(), f.duelLosses(), f.duelDraws()));
        out.add(line("battles", "Null Realm battles: %s played, %s won, %s knockouts.", f.battlesPlayed(), f.battlesWon(), f.battleKos()));

        out.add(f.knownKi().isEmpty()
                ? line("known_none", "Knows nobody's ki yet.")
                : line("known", "Known ki (%s): %s.", f.knownKi().size(), names(f.knownKi(), MAX_NAMES)));

        out.add(line("forms", "Forms reached: %s. Fusion partners: %s.", f.firstForms(), f.fusions()));
        out.add(f.destructionOff()
                ? line("destruction_off", "Destruction: off (they switched it off for themselves).")
                : line("destruction_on", "Destruction: on."));
        if (f.realmReturn()) out.add(line("realm_return", "Has a saved Null Realm return point."));

        if (f.online()) {
            if (f.now().isEmpty()) out.add(line("now_none", "Right now: nothing in progress."));
            else out.addAll(f.now());
        }
        return out;
    }

    // ---- what an online player is in the middle of, and the command's own replies

    public static Line nowDuel(String opponent) {
        return line("now_duel", "Right now: in a duel with %s.", opponent);
    }

    public static Line nowBattle() {
        return line("now_battle", "Right now: in a Null Realm battle.");
    }

    public static Line nowChamber(int zone) {
        return line("now_chamber", "Right now: in the Time Chamber, depth zone %s.", zone);
    }

    public static Line nowScouter(String channel) {
        return line("now_scouter", "Right now: wearing a scouter on the %s channel.", channel);
    }

    public static Line nowDiet(String diet) {
        return line("now_diet", "Right now: on the %s diet.", capitalize(diet.toLowerCase(Locale.ROOT)));
    }

    public static Line nowShadow() {
        return line("now_shadow", "Right now: their rival's shadow is out.");
    }

    public static Line pickOne() {
        return line("one", "Pick one player to inspect.");
    }

    public static Line noData(String name) {
        return line("no_data", "%s has no saved data on this server.", name);
    }

    /** "x1.5", "x2": two decimals at most, trailing zeros dropped. */
    public static String times(double multiplier) {
        BigDecimal d = BigDecimal.valueOf(multiplier).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return "x" + d.toPlainString();
    }

    /** "A, B and C", cut to the first {@code max} names with "and N more". */
    static String names(List<String> names, int max) {
        if (names.size() <= max) {
            if (names.size() == 1) return names.get(0);
            return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
        }
        return String.join(", ", names.subList(0, max)) + " and " + (names.size() - max) + " more";
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
