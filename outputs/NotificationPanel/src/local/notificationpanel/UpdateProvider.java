package local.notificationpanel;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Shares only the verified update APK, read-only, via a temporary URI grant. */
public final class UpdateProvider extends ContentProvider {
    public static final Uri URI=Uri.parse("content://local.notificationpanel.updates/ready.apk");
    static File file(Context c){return new File(c.getCacheDir(),"ready.apk");}
    private void check(Uri uri){if(!URI.equals(uri))throw new IllegalArgumentException("Unknown update URI");}
    public boolean onCreate(){return true;}
    public String getType(Uri uri){check(uri);return "application/vnd.android.package-archive";}
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        check(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        check(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++)if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="NotificationPanel-update.apk";else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file(getContext()).length();
        c.addRow(row);return c;
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
