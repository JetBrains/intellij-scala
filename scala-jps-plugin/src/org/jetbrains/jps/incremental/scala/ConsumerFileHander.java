package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.jetbrains.jps.incremental.scala.Utilities.toCanonicalPath;

/**
* @author Pavel Fatin
*/
class ConsumerFileHander implements FileHandler {
  private ChunkBuildOutputConsumer myConsumer;
  private Map<File, BuildTarget> myMap;

  ConsumerFileHander(ChunkBuildOutputConsumer consumer, Map<File, BuildTarget> map) {
    myConsumer = consumer;
    myMap = map;
  }

  public void processFile(File source, File module) throws IOException {
    BuildTarget target = myMap.get(source);
    // Don't report the input, because we don't need external dependency processing
    myConsumer.registerOutputFile(target, toCanonicalPath(module), Collections.<String>emptyList());
  }
}
