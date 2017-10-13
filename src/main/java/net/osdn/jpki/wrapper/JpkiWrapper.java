package net.osdn.jpki.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;

public class JpkiWrapper {
	
	private Pattern ERROR_CODE_PATTERN = Pattern.compile("!ErrorCode=([-]?[0-9]+),WinErrorCode=([-]?[0-9]+)");
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
				addLibraryPath(jpkiInstallPath.getAbsolutePath());
				boolean is64 = is64bitJavaVM();
				System.loadLibrary(is64 ? "JPKICSPSign64" : "JPKICSPSign");
				loader = createClassLoader(jpkiInstallPath);
				clsJpkiWrapper = Class.forName("net.osdn.jpki.wrapper.internal.JpkiWrapperInternal", true, loader);
			}
		} catch (IOException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (SecurityException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			initializeError = e;
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			initializeError = e;
			e.printStackTrace();
		}
	}

	private Object instance;
	private Method methodSetApplicationName;
	private Method methodSetApplicationVersion;
	private Method methodAddSignature;
	
	public JpkiWrapper() throws JpkiException, IOException, ClassNotFoundException, NoSuchFieldException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException {
		if(initializeError != null) {
			if(initializeError instanceof JpkiException) {
				throw (JpkiException)initializeError;
			} else if(initializeError instanceof IOException) {
				throw (IOException)initializeError;
			} else if(initializeError instanceof ClassNotFoundException) {
				throw (ClassNotFoundException)initializeError;
			} else if(initializeError instanceof NoSuchFieldException) {
				throw (NoSuchFieldException)initializeError;
			} else if(initializeError instanceof SecurityException) {
				throw (SecurityException)initializeError;
			} else if(initializeError instanceof IllegalArgumentException) {
				throw (IllegalArgumentException)initializeError;
			} else if(initializeError instanceof IllegalAccessException) {
				throw (IllegalAccessException)initializeError;
			} else {
				throw new JpkiException(initializeError.getMessage(), initializeError.getLocalizedMessage(), initializeError);
			}
		}
		instance = clsJpkiWrapper.newInstance();
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
	
	private static ClassLoader createClassLoader(File jpkiInstallPath) throws ClassNotFoundException, IOException {
		boolean is64 = is64bitJavaVM();
		
		List<URL> jars = new ArrayList<URL>();
		File jarJPKICryptSignJNI = new File(jpkiInstallPath, (is64 ? "JPKICryptSignJNI64.jar" : "JPKICryptSignJNI.jar"));
		if(jarJPKICryptSignJNI.exists()) {
			jars.add(jarJPKICryptSignJNI.toURI().toURL());
		}
		File jarJPKIUserCertService = new File(jpkiInstallPath, (is64 ? "JPKIUserCertService64.jar" : "JPKIUserCertService.jar"));
		if(jarJPKIUserCertService.exists()) {
			jars.add(jarJPKIUserCertService.toURI().toURL());
		}
		URLClassLoader loader = new InternalClassLoader(is64, jars, JpkiWrapper.class.getClassLoader());
		return loader;
	}
	
	private static boolean is64bitJavaVM() {
		String s = System.getProperty("sun.arch.data.model");
		if("64".equals(s)) {
			return true;
		}
		if("32".equals(s)) {
			return false;
		}
		return false;
	}
	
	private static File getJpkiInstallPath() {
		String s = null;
		try {
			s = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\JPKI", "InstallPath");
		} catch(Win32Exception e) {
			if(e.getErrorCode() == 2) {
				//「指定されたファイルが見つかりません」の時はスタックトレースを出力しません。
			} else {
				e.printStackTrace();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		if(s != null) {
			File dir = new File(s);
			if(dir.exists() && dir.isDirectory()) {
				return dir;
			}
		}
		return null;
	}
	
	private static void addLibraryPath(String path) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		System.setProperty("java.library.path", path + ";" + System.getProperty("java.library.path"));
		 
		Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
		sysPathsField.setAccessible(true);
		sysPathsField.set(null, null);
	}
}
