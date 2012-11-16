package org.jetbrains.jps.incremental.scala;

import java.io.File;
import java.io.IOException;

/**
 * @author Pavel Fatin
 */
public interface FileHandler {
  void processFile(File source, File module) throws IOException;
}
