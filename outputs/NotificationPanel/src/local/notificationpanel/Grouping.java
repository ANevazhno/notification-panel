package local.notificationpanel;

import java.util.*;

/** Pure grouping policy, independently testable without Android. */
public final class Grouping {
    public interface Entry {
        String key(); String appKey(); String originalGroup();
        boolean summary(); boolean excluded(); long time();
    }
    public static <T extends Entry> LinkedHashMap<String, List<T>> group(Collection<T> input) {
        Map<String,T> unique = new LinkedHashMap<>();
        for (T e : input) unique.put(e.key(), e);
        Set<String> childGroups = new HashSet<>();
        for (T e : unique.values()) if (!e.summary() && e.originalGroup()!=null)
            childGroups.add(e.appKey()+"\u0000"+e.originalGroup());
        List<T> items = new ArrayList<>();
        for (T e : unique.values()) {
            if (e.excluded()) continue;
            if (e.summary() && childGroups.contains(e.appKey()+"\u0000"+e.originalGroup())) continue;
            items.add(e);
        }
        items.sort((a,b)->Long.compare(b.time(),a.time()));
        LinkedHashMap<String,List<T>> groups = new LinkedHashMap<>();
        for (T e : items) groups.computeIfAbsent(e.appKey(), k->new ArrayList<>()).add(e);
        return groups;
    }
}
