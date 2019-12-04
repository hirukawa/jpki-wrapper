package net.osdn.jpki.wrapper;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JpkiWrapper {

    private static volatile ClassLoader loader;

    public static boolean isAvailable() {
        return getJpkiInstallPath() != null;
    }

    private String applicationName;
    private String applicationVersion;
    private JpkiWrapperInternal impl;

    public JpkiWrapper() throws JpkiException, IOException, ReflectiveOperationException {
        if(loader == null) {
            synchronized (JpkiWrapper.class) {
                if(loader == null) {
                    File jpkiInstallPath = getJpkiInstallPath();
                    if(jpkiInstallPath == null) {
                        throw new JpkiException(
                                "JPKI user software was not found. Make sure that the JPKI user software is correctly installed.",
                                "JPKI利用者ソフトが見つかりませんでした。JPKI利用者ソフトが正しくインストールされていることを確認してください。",
                                null
                        );
                    }
                    loader = createLoader(jpkiInstallPath);
                }
            }
        }
        Class<?> cls = loader.loadClass("net.osdn.jpki.wrapper.internal.JpkiWrapperImpl");
        impl = (JpkiWrapperInternal)cls.getConstructor().newInstance();
    }

    public void setApplicationName(String name) {
        applicationName = name;
    }

    public void setApplicationVersion(String version) {
        applicationVersion = version;
    }

    public void addSignature(OutputStream output, PDDocument document) throws JpkiException, IOException {
        addSignature(output, document, null, null, null, null, null, null);
    }

    public void addSignature(OutputStream output, PDDocument document, SignatureOptions options) throws JpkiException, IOException {
        addSignature(output, document, null, null, null, null, null, options);
    }

    public void addSignature(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options) throws JpkiException, IOException {
        try {
            impl.addSignature(output, document, name, reason, date, location, contact, options, applicationName, applicationVersion);
        } catch(IOException e) {
            String message = e.getMessage();
            if(message != null && message.charAt(0) == '!') {
                Matcher m = ERROR_CODE_PATTERN.matcher(message);
                if(m.matches()) {
                    int errorCode = Integer.parseInt(m.group(1));
                    int winErrorCode = Integer.parseInt(m.group(2));
                    throw new JpkiException(errorCode, winErrorCode, e.getCause());
                }
            }
            throw e;
        }
    }

    private static Pattern ERROR_CODE_PATTERN = Pattern.compile("!ErrorCode=(-?[0-9]+),WinErrorCode=(-?[0-9]+)");

    private static ClassLoader createLoader(File jpkiInstallPath) throws IOException {
        String[] jarNames = is64bitJavaVM() ?
                new String[] { "JPKICryptSignJNI64.jar", "JPKIUserCertService64.jar" }:
                new String[] { "JPKICryptSignJNI.jar",   "JPKIUserCertService.jar" };

        List<URL> urls = new ArrayList<URL>();
        for(String jarName : jarNames) {
            File jar = new File(jpkiInstallPath, jarName);
            if(jar.exists()) {
                urls.add(jar.toURI().toURL());
            }
        }

        String internalJar = is64bitJavaVM() ?
                "/jpki-wrapper-internal64.jar":
                "/jpki-wrapper-internal32.jar";

        try(InputStream in = JpkiWrapper.class.getResourceAsStream(internalJar);
                JarInputStream jar = new JarInputStream(in)) {
            ClassLoader loader = new InternalClassLoader(urls.toArray(new URL[]{}), jar);
            return loader;
        }
    }

    private static boolean is64bitJavaVM() {
        String s = System.getProperty("os.arch");
        return s != null && s.contains("64");
    }

    private static File getJpkiInstallPath() {
        // find from registry
        try {
            String path1 = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\JPKI", "InstallPath");
            if(path1 != null) {
                File dir = new File(path1);
                if(dir.isDirectory()) {
                    return dir;
                }
            }
        } catch(Exception e) {
            if(e instanceof Win32Exception && ((Win32Exception)e).getErrorCode() == 2) {
                //「指定されたファイルが見つかりません」の時はスタックトレースを出力しません。
            } else {
                e.printStackTrace();
            }
        }

        // find from PATH env
        String path2 = findPath("JPKIMenu.exe");
        if(path2 != null) {
            return new File(path2).getParentFile();
        }

        // not found
        return null;
    }

    private static String findPath(String executableFilename) {
        char[] pszPath = new char[1024];
        Pointer ppszOtherDirs = Pointer.NULL;

        char[] src = executableFilename.toCharArray();
        System.arraycopy(src, 0, pszPath, 0, src.length);
        boolean ret = Shlwapi.INSTANCE.PathFindOnPathW(pszPath, ppszOtherDirs);
        if(ret) {
            return new String(pszPath).trim();
        } else {
            return null;
        }
    }

    public interface Shlwapi extends Library {
        Shlwapi INSTANCE = Native.load("shlwapi", Shlwapi.class);

        boolean PathFindOnPathW(char[] pszPath, Pointer ppszOtherDirs);
    }
}
