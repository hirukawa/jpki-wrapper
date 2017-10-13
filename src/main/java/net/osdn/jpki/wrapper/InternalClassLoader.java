package net.osdn.jpki.wrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.pdfbox.io.IOUtils;

public class InternalClassLoader extends URLClassLoader {

	private Map<String, byte[]> entries = new HashMap<String, byte[]>();
	
	public InternalClassLoader(boolean is64, List<URL> urls, ClassLoader parent) throws IOException {
		super(urls.toArray(new URL[] {}), parent);

		byte[] internalJar = null;
		/*
		File file = new File("jpki-wrapper-internal/build/libs/jpki-wrapper-internal.jar");
		if(file.exists()) {
			try(InputStream is = new FileInputStream(file)) {
				internalJar = IOUtils.toByteArray(is);
			}
		}
		*/
		if(internalJar == null) {
			InputStream is = null;
			if(is64) {
				is = getClass().getResourceAsStream("/jpki-wrapper-internal64.jar");
			} else {
				is = getClass().getResourceAsStream("/jpki-wrapper-internal32.jar");
			}
			try {
				if(is != null) {
					internalJar = IOUtils.toByteArray(is);
				}
			} finally {
				if(is != null) {
					try { is.close(); } catch(Exception e) {}
				}
			}
		}
		
		if(internalJar != null) {
			try(JarInputStream jar = new JarInputStream(new ByteArrayInputStream(internalJar))) {
				JarEntry entry;
				while((entry = jar.getNextJarEntry()) != null) {
					byte[] bytes = readJarEntryBytes(jar);
					entries.put(entry.getName(), bytes);
				}
			}
		}
	}
	
	private static byte[] readJarEntryBytes(JarInputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] bytes = new byte[65536];
		int len = 0;
		while(len != -1) {
			len = in.read(bytes);
			if(len > 0) {
				buf.write(bytes, 0, len);
			}
		}
		return buf.toByteArray();
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String entryName = name.replace('.',  '/') + ".class";
		byte[] bytes = entries.get(entryName);
		if(bytes != null) {
			return defineClass(name, bytes, 0, bytes.length);
		} else {
			return super.findClass(name);
		}
	}
}
