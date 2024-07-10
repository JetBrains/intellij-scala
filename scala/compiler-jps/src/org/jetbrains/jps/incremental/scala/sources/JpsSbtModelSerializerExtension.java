package org.jetbrains.jps.incremental.scala.sources;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import scala.Option;

import java.util.Arrays;
import java.util.List;

public class JpsSbtModelSerializerExtension extends JpsModelSerializerExtension {

  // should be in sync with org.jetbrains.sbt.project.SbtProjectSystem.Id
  private static final String SBT_SYSTEM_ID = "SBT";

  @NotNull
  @Override
  public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Arrays.asList(new SbtModuleSerializer(), new SharedSourcesModuleSerializer());
  }

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    // note: if we store module files externally, this part will require rewriting.
    // See how org.jetbrains.jps.gradle.model.impl.JpsGradleModelSerializationExtension#loadModuleOptions is implemented.
    boolean isSbtModule = SBT_SYSTEM_ID.equals(rootElement.getAttributeValue("external.system.id"));
    if (isSbtModule) {
      Option<String> type = Option.apply(rootElement.getAttributeValue("external.system.module.type"));
      JpsSbtExtensionService.getInstance().getOrCreateExtension(module, type);
    }
  }
}
