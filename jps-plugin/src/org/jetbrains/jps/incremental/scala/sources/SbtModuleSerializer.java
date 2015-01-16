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
public class SbtModuleSerializer extends JpsModulePropertiesSerializer<JpsDummyElement> {
  public SbtModuleSerializer() {
    super(SbtModuleType.INSTANCE, "SBT_MODULE", "dummy");
  }

  @Override
  public JpsDummyElement loadProperties(@Nullable Element componentElement) {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element componentElement) {

  }
}
