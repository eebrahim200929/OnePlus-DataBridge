package sa.databridge.oneplus;

import android.os.RemoteException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/** Executes in Shizuku's shell/root-identity user service. Not in the ordinary app process. */
public class RemoteFilesService extends IFileAccess.Stub {
    private static final String ROOT = "/storage/emulated/0/Android/";

    private static File checked(File f) throws IOException {
        String path = f.getCanonicalPath();
        if (!(path.equals(ROOT + "data") || path.startsWith(ROOT + "data/")
                || path.equals(ROOT + "obb") || path.startsWith(ROOT + "obb/"))) {
            throw new SecurityException("Only Android/data and Android/obb are supported");
        }
        return new File(path);
    }

    @Override public String[] list(String path) throws RemoteException {
        try {
            File dir = checked(new File(path));
            if (!dir.isDirectory()) throw new IOException("Folder cannot be opened by current Shizuku privileges");
            File[] found = dir.listFiles();
            if (found == null) throw new IOException("Directory listing blocked by the system");
            Arrays.sort(found, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            ArrayList<String> out = new ArrayList<>();
            int count = 0;
            for (File item : found) {
                if (++count > 1500) { out.add("E\tToo many files (1500 limit)"); break; }
                String kind = item.isDirectory() ? "D" : "F";
                String name = item.getName().replace('\t', ' ').replace('\n', ' ');
                out.add(kind + "\t" + name);
            }
            return out.toArray(new String[0]);
        } catch (Exception e) {
            return new String[] {"E\t" + e.getClass().getSimpleName() + ": " + e.getMessage()};
        }
    }

    @Override public String copyToDownloads(String sourcePath) throws RemoteException {
        try {
            File src = checked(new File(sourcePath));
            if (!src.isFile()) return "Select a regular file, not a folder";
            File downloads = new File("/storage/emulated/0/Download");
            if (!downloads.exists() && !downloads.mkdirs()) return "Download folder is not writable";
            String original = src.getName();
            if (original.contains("/") || original.contains("\\")) return "Invalid file name";
            File target = new File(downloads, original);
            int n = 1;
            int pos = original.lastIndexOf('.');
            String stem = pos <= 0 ? original : original.substring(0, pos);
            String ext = pos <= 0 ? "" : original.substring(pos);
            while (target.exists() && n < 10000) target = new File(downloads, stem + " (" + n++ + ")" + ext);
            if (target.exists()) return "No safe destination filename available";
            try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[131072];
                int bytes;
                while ((bytes = in.read(buffer)) != -1) out.write(buffer, 0, bytes);
                out.getFD().sync();
            } catch (IOException e) { target.delete(); throw e; }
            return "OK: " + target.getAbsolutePath();
        } catch (Exception e) { return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage(); }
    }
}
