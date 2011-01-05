package me.taylorkelly.bigbrother;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;

public class PropertiesFile {
	private HashMap<String, PropertiesEntry> map;
	private File file;

	public PropertiesFile(File file) {
		this.file = file;
		map = new HashMap<String, PropertiesEntry>();
		Scanner scan;
		try {
			if (!file.exists())
				file.createNewFile();
			scan = new Scanner(file);
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				if (!line.contains("="))
					continue;
				int equals = line.indexOf("=");
				int commentIndex = line.length();
				if (line.contains("#")) {
					commentIndex = line.indexOf("#");
				}

				String key = line.substring(0, equals).trim();
				if (key.equals(""))
					continue;
				String value = line.substring(equals + 1, commentIndex).trim();
				String comment = "";
				if (commentIndex < line.length() - 1) {
					comment = line.substring(commentIndex + 1, line.length()).trim();
				}
				map.put(key, new PropertiesEntry(value, comment));
			}
		} catch (FileNotFoundException e) {
			BigBrother.log.log(Level.SEVERE, "[BBROTHER]: Cannot read file " + file.getName());
		} catch (IOException e) {
			BigBrother.log.log(Level.SEVERE, "[BBROTHER]: Cannot create file " + file.getName());
		}
	}

	public boolean getBoolean(String key, Boolean defaultValue, String defaultComment) {
		if (map.containsKey(key)) {
			return Boolean.parseBoolean(map.get(key).value);
		} else {
			map.put(key, new PropertiesEntry(defaultValue.toString(), defaultComment));
			return defaultValue;
		}
	}

	public String getString(String key, String defaultValue, String defaultComment) {
		if (map.containsKey(key)) {
			return map.get(key).value;
		} else {
			map.put(key, new PropertiesEntry(defaultValue.toString(), defaultComment));
			return defaultValue;
		}
	}

	public int getInt(String key, Integer defaultValue, String defaultComment) {
		if (map.containsKey(key)) {
			try {
				return Integer.parseInt(map.get(key).value);
			} catch (Exception e) {
				BigBrother.log.log(Level.WARNING, "[BBROTHER]: Trying to get Integer from " + key + ": " + map.get(key).value);
				return 0;
			}
		} else {
			map.put(key, new PropertiesEntry(defaultValue.toString(), defaultComment));
			return defaultValue;
		}
	}

	public double getDouble(String key, Double defaultValue, String defaultComment) {
		if (map.containsKey(key)) {
			try {
				return Double.parseDouble(map.get(key).value);
			} catch (Exception e) {
				BigBrother.log.log(Level.WARNING, "[BBROTHER]: Trying to get Double from " + key + ": " + map.get(key).value);
				return 0;
			}
		} else {
			map.put(key, new PropertiesEntry(defaultValue.toString(), defaultComment));
			return defaultValue;
		}
	}

	public void save() {
		BufferedWriter bwriter = null;
		FileWriter fwriter = null;
		try {
			if (!file.exists())
				file.createNewFile();
			fwriter = new FileWriter(file, true);
			bwriter = new BufferedWriter(fwriter);
			for (Entry<String, PropertiesEntry> entry : map.entrySet()) {
				StringBuilder builder = new StringBuilder();
				builder.append(entry.getKey());
				builder.append("=");
				builder.append(entry.getValue().value);
				if(!entry.getValue().comment.equals("")) {
					builder.append(" #");
					builder.append(entry.getValue().comment);
				}
				bwriter.write(builder.toString());
				bwriter.newLine();
			}
			bwriter.flush();
		} catch (IOException e) {
			BigBrother.log.log(Level.SEVERE, "[BBROTHER]: IO Exception with file " + file.getName());
		} finally {
			try {
				if (bwriter != null) {
					bwriter.flush();
					bwriter.close();
				} if (fwriter != null) {
					fwriter.close();
				}
			} catch (IOException e) {
				BigBrother.log.log(Level.SEVERE, "[BBROTHER]: IO Exception with file " + file.getName() + " (on close)");
			}
		}

	}

	private class PropertiesEntry {
		public String value;
		public String comment;

		public PropertiesEntry(String value, String comment) {
			this.value = value;
			this.comment = comment;
		}
	}

}