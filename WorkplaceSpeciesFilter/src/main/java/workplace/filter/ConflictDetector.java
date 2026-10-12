package workplace.filter;

import init.paths.ModInfo;
import init.paths.PATHS;
import snake2d.LOG;
import snake2d.util.sets.LIST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Scans every active mod's script JARs at startup for collisions with the
 * vanilla classes this mod replaces. Two mods that both class-replace the same
 * vanilla file silently override each other (last-loaded wins) — if filters
 * stop enforcing, this log is the only signal a user has to debug it. We log
 * every detected collision once at startup so the conflict shows up in the
 * game log file the user can attach to bug reports.
 *
 * Detection failures NEVER interrupt the game — every external call is wrapped
 * in try/catch.
 */
final class ConflictDetector {

    private static final String OUR_MOD_NAME = "Workplace Species Filter";

    private static final Set<String> REPLACED_CLASSES;
    static {
        Set<String> s = new HashSet<>();
        s.add("settlement/room/main/employment/RoomEmployments.class");
        s.add("settlement/room/main/employment/RoomEmploymentSimple.class");
        s.add("settlement/entity/humanoid/ai/work/AIModule_Work.class");
        s.add("settlement/entity/humanoid/ai/types/guard/AIModule_Guard.class");
        s.add("settlement/entity/humanoid/ai/subwalk/AISUB_walkTo.class");
        s.add("settlement/path/finders/SFinderRoomService.class");
        s.add("settlement/entity/humanoid/ai/service/M_PlanSpectator.class");
        REPLACED_CLASSES = Collections.unmodifiableSet(s);
    }

    private ConflictDetector() {}

    static void runOnce() {
        try {
            LIST<ModInfo> mods = PATHS.currentMods();
            if (mods == null) return;
            int conflicts = 0;
            for (int i = 0; i < mods.size(); i++) {
                ModInfo m = mods.get(i);
                if (m == null) continue;
                if (OUR_MOD_NAME.equals(m.name)) continue;
                conflicts += checkMod(m);
            }
            if (conflicts == 0) {
                LOG.ln("[WorkplaceSpeciesFilter] No class-replacement conflicts detected with other active mods.");
            } else {
                LOG.ln("[WorkplaceSpeciesFilter] Detected " + conflicts + " class-replacement conflict(s). See lines above. If filters stop enforcing, disable one of the listed mods.");
            }
        } catch (Throwable t) {
            LOG.ln("[WorkplaceSpeciesFilter] Conflict detection failed (non-fatal): " + t);
        }
    }

    private static int checkMod(ModInfo m) {
        int hits = 0;
        try {
            Path scriptFolder = PATHS.local().MODS
                .getFolder(m.path)
                .getFolder("V" + m.majorVersion)
                .get()
                .resolve("script");

            if (!Files.isDirectory(scriptFolder)) return 0;

            List<Path> jars = new ArrayList<>();
            try (Stream<Path> stream = Files.list(scriptFolder)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(jars::add);
            }
            for (Path jar : jars) {
                hits += checkJar(m, jar);
            }
        } catch (Throwable t) {
            // Per-mod failures are silent: a broken/unreadable mod folder is not our problem
        }
        return hits;
    }

    private static int checkJar(ModInfo m, Path jar) {
        List<String> matches = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                String name = je.getName();
                if (REPLACED_CLASSES.contains(name)) {
                    matches.add(name);
                }
            }
        } catch (IOException e) {
            return 0;
        }
        if (matches.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        sb.append("[WorkplaceSpeciesFilter] CONFLICT: mod '").append(m.name)
          .append("' (").append(jar.getFileName())
          .append(") also class-replaces: ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(matches.get(i));
        }
        LOG.ln(sb.toString());
        return matches.size();
    }
}
