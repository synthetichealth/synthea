package org.mitre.synthea.export;

import java.io.FileWriter;
import java.io.IOException;

/**
 * The CPCDSPostProcess class contains any and all scripts related to the post
 * processing of the CPCDS exported files.
 */
public class CPCDSPostProcess {
	
	/**
	 * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
	 */
	private static final String NEWLINE = System.lineSeparator();

	/**
	 * Constructor for the CPCDSPostProcess class
	 */
	public CPCDSPostProcess() {
		super();
	}	
  
  /**
   * Helper method to write a line to a File. Extracted to a separate method here
   * to make it a little easier to replace implementations.
   *
   * @param line   The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, FileWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
}
