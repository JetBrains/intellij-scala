package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
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

  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(new FacetSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new ProjectSettingsSerializer());
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

  private static class ProjectSettingsSerializer extends JpsProjectExtensionSerializer {
    private ProjectSettingsSerializer() {
      super("scala_compiler.xml", "ScalaCompilerConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      ProjectSettingsImpl.State state = XmlSerializer.deserialize(componentTag, ProjectSettingsImpl.State.class);
      ProjectSettingsImpl settings = new ProjectSettingsImpl(state == null ? new ProjectSettingsImpl.State() : state);
      SettingsManager.setProjectSettings(jpsProject, settings);
    }

    @Override
    public void saveExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
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
