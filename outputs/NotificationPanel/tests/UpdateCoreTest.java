import local.notificationpanel.UpdateCore;
import java.io.*;
import java.nio.file.*;

public final class UpdateCoreTest {
    interface Task {void run() throws Exception;}
    static void rejects(Task t) throws Exception {try{t.run();}catch(IOException expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args) throws Exception {
        UpdateCore.https("https://example.org/latest.json");
        rejects(()->UpdateCore.https("http://example.org/update.apk"));
        rejects(()->UpdateCore.https("https://user:secret@example.org/a"));
        rejects(()->UpdateCore.https("https://example.org/a#fragment"));
        rejects(()->UpdateCore.copy(new ByteArrayInputStream(new byte[11]),new ByteArrayOutputStream(),10));
        ByteArrayOutputStream out=new ByteArrayOutputStream();UpdateCore.copy(new ByteArrayInputStream(new byte[10]),out,10);
        if(out.size()!=10)throw new AssertionError();
        File file=File.createTempFile("panel-hash",".tmp");
        try{Files.write(file.toPath(),"abc".getBytes("UTF-8"));
            UpdateCore.verifyHash(file,"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            rejects(()->UpdateCore.verifyHash(file,"0000000000000000000000000000000000000000000000000000000000000000"));
            rejects(()->UpdateCore.verifyHash(file,"invalid"));
        }finally{file.delete();}
        System.out.println("PASS UpdateCore: HTTPS policy, credentials, fragment, size limit, valid hash, corrupt hash");
    }
}
