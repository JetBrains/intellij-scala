package org.jetbrains.jps.incremental.scala.sources;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class SbtSerializerService extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Arrays.asList(new SbtModuleSerializer(), new SharedSourcesModuleSerializer());
  }
}
