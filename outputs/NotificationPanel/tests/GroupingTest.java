import local.notificationpanel.Grouping;
import java.util.*;
public class GroupingTest {
    static class E implements Grouping.Entry {
        String k,a,g; boolean s,x; long t;
        E(String k,String a,String g,boolean s,boolean x,long t){this.k=k;this.a=a;this.g=g;this.s=s;this.x=x;this.t=t;}
        public String key(){return k;} public String appKey(){return a;} public String originalGroup(){return g;}
        public boolean summary(){return s;} public boolean excluded(){return x;} public long time(){return t;}
    }
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        List<E> input=Arrays.asList(
            new E("sum","0:compass","one",true,false,10),
            new E("chat1","0:compass","one",false,false,11),
            new E("chat2","0:compass","two",false,false,12),
            new E("chat2","0:compass","two",false,false,15),
            new E("work","10:compass","one",false,false,14),
            new E("service","0:weather",null,false,true,20),
            new E("onlysummary","0:mail","mail",true,false,13),
            new E("hiddenSummary","0:music","player",true,false,30),
            new E("hiddenChild","0:music","player",false,true,30));
        Map<String,List<E>> out=Grouping.group(input);
        check(out.size()==3,"Exclude service, keep work profile separate");
        check(out.get("0:compass").size()==2,"Merge distinct groups; skip summary; deduplicate updates");
        check(out.get("0:compass").get(0).time()==15,"Newest update first");
        check(out.get("0:mail").size()==1,"Preserve summary when no children available");
        check(!out.containsKey("0:music"),"Do not expose summary for excluded children");
        check(out.keySet().iterator().next().equals("0:compass"),"Order applications by newest notification");
        check(Grouping.group(Collections.<E>emptyList()).isEmpty(),"Empty state");
        System.out.println("PASS: grouping, updates, summaries, profiles, exclusions, ordering, empty state");
    }
}
