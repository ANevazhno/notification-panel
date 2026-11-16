package local.notificationpanel;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

/** No notification data is referenced by the update client. */
public final class UpdateCore {
    private UpdateCore() {}
    public static URL https(String value) throws IOException {
        URL u=new URL(value);
        if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getRef()!=null)
            throw new IOException("Нужен HTTPS-адрес без пароля и фрагмента");
        return u;
    }
    public static void fetch(String address,OutputStream out,long limit) throws IOException {
        URL url=https(address);
        for(int redirects=0;redirects<=5;redirects++){
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept-Encoding","identity");c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");
            try{
                int status=c.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String next=c.getHeaderField("Location");if(next==null)throw new IOException("Пустое перенаправление");
                    url=https(new URL(url,next).toExternalForm());continue;
                }
                if(status!=200)throw new IOException("Сервер вернул HTTP "+status);
                if(c.getContentLengthLong()>limit)throw new IOException("Файл слишком большой");
                try(InputStream in=c.getInputStream()){copy(in,out,limit);}return;
            }finally{c.disconnect();}
        }
        throw new IOException("Слишком много перенаправлений");
    }
    public static void copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buf=new byte[16384];long total=0;int n;
        while((n=in.read(buf))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("Загрузка остановлена");
            total+=n;if(total>limit)throw new IOException("Файл слишком большой");out.write(buf,0,n);
        }
    }
    public static String sha256(File file) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    public static void verifyHash(File file,String expected) throws Exception {
        if(expected==null||!expected.matches("[a-fA-F0-9]{64}")||!sha256(file).equalsIgnoreCase(expected))
            throw new IOException("Контрольная сумма APK не совпадает");
    }
}
