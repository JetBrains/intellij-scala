package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.FacetSettingsImpl;
import org.jetbrains.jps.incremental.scala.model.GlobalSettingsImpl;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ScalaSerializerService extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new GlobalSettingsSerializer());
  }

  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(new FacetSettingsSerializer());
  }

  private static class GlobalSettingsSerializer extends JpsGlobalExtensionSerializer {
    private GlobalSettingsSerializer() {
      super("scala.xml", "ScalaSettings");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal jpsGlobal, @NotNull Element componentTag) {
      GlobalSettingsImpl.State state = XmlSerializer.deserialize(componentTag, GlobalSettingsImpl.State.class);
      GlobalSettingsImpl settings = new GlobalSettingsImpl(state == null ? new GlobalSettingsImpl.State() : state);
      SettingsManager.setGlobalSettings(jpsGlobal, settings);
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal jpsGlobal, @NotNull Element componentTag) {
      // do nothing
    }
  }

  private static class FacetSettingsSerializer extends JpsFacetConfigurationSerializer<FacetSettings> {
    public FacetSettingsSerializer() {
      super(SettingsManager.FACET_SETTINGS_ROLE, "scala", null);
    }

    @Override
    protected FacetSettings loadExtension(@NotNull Element facetConfigurationElement, String name, JpsElement parent, JpsModule module) {
      FacetSettingsImpl.State state = XmlSerializer.deserialize(facetConfigurationElement, FacetSettingsImpl.State.class);
      return new FacetSettingsImpl(state);
    }

    @Override
    protected void saveExtension(FacetSettings extension, Element facetConfigurationTag, JpsModule module) {
      // do nothing
    }
  }
}
