package net.osdn.jpki.wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class InternalClassLoader extends URLClassLoader {

	private Map<String, byte[]> entries = new HashMap<String, byte[]>();

	public InternalClassLoader(URL[] urls, JarInputStream jar) throws IOException {
		super(urls);

		JarEntry entry;
		while((entry = jar.getNextJarEntry()) != null) {
			byte[] bytes = readJarEntryBytes(jar);
			entries.put(entry.getName(), bytes);
		}
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
}
