package net.osdn.jpki.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

public class JpkiWrapper {
	
	private Pattern ERROR_CODE_PATTERN = Pattern.compile("!ErrorCode=(-?[0-9]+),WinErrorCode=([-]?[0-9]+)");
	private static ClassLoader loader;
	private static Class<?> clsJpkiWrapper;
	private static Throwable initializeError;
	
	static {
		try {
			File jpkiInstallPath = getJpkiInstallPath();
			if(jpkiInstallPath == null) {
				initializeError = new JpkiException(
					"JPKI user software was not found. Make sure that the JPKI user software is correctly installed.",
					"JPKI利用者ソフトが見つかりませんでした。JPKI利用者ソフトが正しくインストールされていることを確認してください。",
					null
				);
			} else {
				loader = createClassLoader(jpkiInstallPath);
				clsJpkiWrapper = Class.forName("net.osdn.jpki.wrapper.internal.JpkiWrapperInternal", true, loader);
			}
		}
		catch (IOException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			initializeError = e;
			e.printStackTrace();
		} catch(Exception e) {
			initializeError = e;
			e.printStackTrace();
		}
	}

	private Object instance;
	private Method methodSetApplicationName;
	private Method methodSetApplicationVersion;
	private Method methodAddSignature;
	
	public JpkiWrapper() throws JpkiException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
		if(initializeError != null) {
			if(initializeError instanceof JpkiException) {
				throw (JpkiException)initializeError;
			} else if(initializeError instanceof IOException) {
				throw (IOException)initializeError;
			} else if(initializeError instanceof ClassNotFoundException) {
				throw (ClassNotFoundException)initializeError;
			} else {
				throw new JpkiException(initializeError.getMessage(), initializeError.getLocalizedMessage(), initializeError);
			}
		}
		instance = clsJpkiWrapper.getConstructor().newInstance();
		methodSetApplicationName = clsJpkiWrapper.getDeclaredMethod("setApplicationName", String.class);
		methodSetApplicationVersion = clsJpkiWrapper.getDeclaredMethod("setApplicationVersion", String.class);
		methodAddSignature = clsJpkiWrapper.getDeclaredMethod("addSignature", OutputStream.class, PDDocument.class, String.class, String.class, Date.class, String.class, String.class, SignatureOptions.class);
	}
	
	public void setApplicationName(String name) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		methodSetApplicationName.invoke(instance, name);
	}
	
	public void setApplicationVersion(String version) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		methodSetApplicationVersion.invoke(instance, version);
	}

	public void addSignature(OutputStream output, PDDocument document) throws JpkiException, IOException, InvocationTargetException, IllegalAccessException, IllegalArgumentException {
		addSignature(output, document, null, null, null, null, null, null);
	}

	public void addSignature(OutputStream output, PDDocument document, SignatureOptions options) throws JpkiException, IOException, InvocationTargetException, IllegalAccessException, IllegalArgumentException {
		addSignature(output, document, null, null, null, null, null, options);
	}

	public void addSignature(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options) throws JpkiException, IOException, InvocationTargetException, IllegalAccessException, IllegalArgumentException {
		try {
			methodAddSignature.invoke(instance, output, document, name, reason, date, location, contact, options);
		} catch (InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if(t instanceof IOException) {
				String message = t.getMessage();
				if(message != null && message.charAt(0) == '!') {
					Matcher m = ERROR_CODE_PATTERN.matcher(message);
					if(m.matches()) {
						int errorCode = Integer.parseInt(m.group(1));
						int winErrorCode = Integer.parseInt(m.group(2));
						throw new JpkiException(errorCode, winErrorCode, t.getCause());
					}
				}
				throw (IOException)t;
			}
			throw e;
		}
	}

	/* package private */ static boolean is64bitJavaVM() {
		String s = System.getProperty("os.arch");
		return s != null && s.contains("64");
	}

	private static ClassLoader createClassLoader(File jpkiInstallPath) throws ClassNotFoundException, IOException {
		boolean is64 = is64bitJavaVM();

		String[] jarNames = is64 ?
				new String[] { "JPKICryptSignJNI64.jar", "JPKIUserCertService64.jar" }:
				new String[] { "JPKICryptSignJNI.jar",   "JPKIUserCertService.jar" };

		List<URL> jars = new ArrayList<URL>();
		for(String jarName : jarNames) {
			File jar = new File(jpkiInstallPath, jarName);
			if(jar.exists()) {
				jars.add(jar.toURI().toURL());
			}
		}

		URLClassLoader loader = new InternalClassLoader(jars, JpkiWrapper.class.getClassLoader());
		return loader;
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
		Shlwapi INSTANCE = (Shlwapi)Native.load("shlwapi", Shlwapi.class);

		boolean PathFindOnPathW(char[] pszPath, Pointer ppszOtherDirs);
	}
}
