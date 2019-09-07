package net.osdn.jpki.wrapper;

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

public class InternalClassLoader extends URLClassLoader {

	private static volatile Map<String, byte[]> entries;

	private static void initialize() throws IOException {
		String internalJarName = JpkiWrapper.is64bitJavaVM() ?
				"/jpki-wrapper-internal64.jar":
				"/jpki-wrapper-internal32.jar";

		Map<String, byte[]> map = new HashMap<String, byte[]>();
		try(InputStream is = InternalClassLoader.class.getResourceAsStream(internalJarName);
			JarInputStream jar = new JarInputStream(is)) {
			JarEntry entry;
			while((entry = jar.getNextJarEntry()) != null) {
				byte[] bytes = readJarEntryBytes(jar);
				map.put(entry.getName(), bytes);
			}
		}
		entries = map;
	}
	
	public InternalClassLoader(List<URL> urls, ClassLoader parent) throws IOException {
		super(urls.toArray(new URL[] {}), parent);

		if(entries == null) {
			synchronized (InternalClassLoader.class) {
				if (entries == null) {
					initialize();
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
