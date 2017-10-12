package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.HydraSettingsImpl;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Maris Alexandru
 */
public class HydraSerializerService  extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new HydraSettingsSerializer());
  }

  private static class HydraSettingsSerializer extends JpsProjectExtensionSerializer {
    private HydraSettingsSerializer() {
      super("hydra.xml", "HydraSettings");
    }

    @Override
    public void loadExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      HydraSettingsImpl.State state = XmlSerializer.deserialize(componentTag, HydraSettingsImpl.State.class);
      HydraSettingsImpl settings = new HydraSettingsImpl(state == null ? new HydraSettingsImpl.State() : state);
      SettingsManager.setHydraSettings(jpsProject, settings);
    }

    @Override
    public void saveExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      // do nothing
    }
  }
}
