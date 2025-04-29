package gov.epa.seqapass.backend.utility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.epa.seqapass.backend.serviceThread.AccessionRun;

public class FileUtil {
	
	private static Logger logger = LogManager.getLogger(FileUtil.class);

	public static boolean mergeFiles(File[] files, File mergedFile) {

		FileWriter fstream = null;
		BufferedWriter out = null;
		try {
			fstream = new FileWriter(mergedFile, true);
			out = new BufferedWriter(fstream);
		} catch (IOException e1) {
			return false;
		}

		for (File f : files) {
			// System.out.println("merging: " + f.getName());
			logger.info("merging: {}", f.getName());
			FileInputStream fis;
			try {
				fis = new FileInputStream(f);
				BufferedReader in = new BufferedReader(new InputStreamReader(fis));

				String aLine;
				while ((aLine = in.readLine()) != null) {
					out.write(aLine);
					out.newLine();
				}

				in.close();
			} catch (IOException e) {
				return false;
			}
		}

		try {
			out.close();
		} catch (IOException e) {
			return false;
		}
		return true;
	}

}
