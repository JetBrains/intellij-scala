package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.GlobalHydraSettingsImpl;
import org.jetbrains.jps.incremental.scala.model.HydraSettingsImpl;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Maris Alexandru
 */
public class HydraSerializerService extends JpsModelSerializerExtension {

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new HydraSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new GlobalHydraSettingsSerializer());
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

  private static class GlobalHydraSettingsSerializer extends JpsGlobalExtensionSerializer {
    private GlobalHydraSettingsSerializer() { super("hydra_config.xml", "HydraApplicationSettings");}

    @Override
    public void loadExtension(@NotNull JpsGlobal jpsGlobal, @NotNull Element componentTag) {
      GlobalHydraSettingsImpl.State state = XmlSerializer.deserialize(componentTag, GlobalHydraSettingsImpl.State.class);
      GlobalHydraSettingsImpl settings = new GlobalHydraSettingsImpl(state == null ? new GlobalHydraSettingsImpl.State() : state);
      SettingsManager.setGlobalHydraSettings(jpsGlobal, settings);
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal jpsGlobal, @NotNull Element componentTag) {
      // do nothing
    }
  }
}
