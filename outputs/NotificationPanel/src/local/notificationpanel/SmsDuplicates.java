package local.notificationpanel;

import android.app.Notification;
import android.app.Person;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Fail open: uncertain, redacted, mixed and attachment notifications stay visible. */
final class SmsDuplicates {
    static List<StatusBarNotification> filter(List<StatusBarNotification> active,List<StatusBarNotification> sms,String defaultPackage){
        if(sms.isEmpty()||defaultPackage==null)return active;
        Set<String> duplicates=new HashSet<>();
        for(StatusBarNotification s:active)if(eligible(s,defaultPackage)&&!summary(s)&&matches(s.getNotification(),sms))duplicates.add(s.getKey());
        for(StatusBarNotification s:active)if(eligible(s,defaultPackage)&&summary(s)){
            boolean any=false,all=true;
            for(StatusBarNotification child:active)if(!summary(child)&&sameGroup(s,child)){any=true;if(!duplicates.contains(child.getKey()))all=false;}
            // A summary without children may be the only visible RCS notification.
            if(any&&all)duplicates.add(s.getKey());
        }
        List<StatusBarNotification> result=new ArrayList<>();for(StatusBarNotification s:active)if(!duplicates.contains(s.getKey()))result.add(s);return result;
    }
    private static boolean eligible(StatusBarNotification s,String pkg){return pkg.equals(s.getPackageName())&&s.getUser().equals(android.os.Process.myUserHandle());}
    private static boolean summary(StatusBarNotification s){return (s.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)!=0;}
    private static boolean sameGroup(StatusBarNotification a,StatusBarNotification b){return a.getGroupKey()!=null&&a.getPackageName().equals(b.getPackageName())&&a.getUser().equals(b.getUser())&&a.getGroupKey().equals(b.getGroupKey());}
    private static boolean matches(Notification n,List<StatusBarNotification> sms){
        Bundle e=n.extras;if(e==null)return false;
        Parcelable[] messages=e.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(messages!=null&&messages.length>0){
            for(Notification.MessagingStyle.Message m:Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)){
                if(m.getDataUri()!=null||m.getDataMimeType()!=null)return false;
                Person sender=m.getSenderPerson();if(sender==null)return false;
                String address=sender.getUri();if(address==null||!address.startsWith("tel:"))address=String.valueOf(sender.getName());
                if(!match(address,m.getText(),m.getTimestamp(),sms))return false;
            }
            // Invalid bundles must never be mistaken for an empty, fully matched set.
            return Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).size()==messages.length;
        }
        if(e.containsKey(Notification.EXTRA_TEXT_LINES)||e.containsKey(Notification.EXTRA_PICTURE))return false;
        CharSequence body=e.getCharSequence(Notification.EXTRA_BIG_TEXT);if(body==null)body=e.getCharSequence(Notification.EXTRA_TEXT);
        return match(String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE)),body,n.when,sms);
    }
    private static boolean match(String sender,CharSequence text,long time,List<StatusBarNotification> sms){
        if(text==null||text.length()==0||time<=0)return false;
        for(StatusBarNotification s:sms){Notification n=s.getNotification();
            if(Math.abs(time-n.when)<=1000&&address(sender).equals(address(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE))))&&text.toString().equals(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT))))return true;
        }return false;
    }
    private static String address(String value){
        if(value==null)return "";String s=value.trim();if(s.startsWith("tel:"))s=s.substring(4);
        // Do not equate unrelated contact names or discard country codes.
        if(s.matches("[+0-9() .-]+"))return s.replaceAll("[() .-]","");return s.toLowerCase(Locale.ROOT);
    }
}
