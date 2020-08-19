package org.mitre.synthea.export;

import java.io.IOException;

/**
 * Exporters that buffer output should implement this interface in order to be notified when
 * all exports are complete and any active output should be flushed to disk.
 */
public interface Flushable {
  void flush() throws IOException;
}
