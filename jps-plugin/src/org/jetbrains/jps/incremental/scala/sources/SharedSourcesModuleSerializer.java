package org.jetbrains.jps.incremental.scala.sources;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;

/**
* @author Pavel Fatin
*/
public class SharedSourcesModuleSerializer extends JpsModulePropertiesSerializer<JpsDummyElement> {
  public SharedSourcesModuleSerializer() {
    super(SharedSourcesModuleType.INSTANCE, "SHARED_SOURCES_MODULE", "dummy");
  }

  @Override
  public JpsDummyElement loadProperties(@Nullable Element componentElement) {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element componentElement) {

  }
}
