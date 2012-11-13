package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.ProjectSettingsImpl;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.FacetSettingsImpl;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ScalaModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new CompilerSettingsSerializer());
  }

  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(new FacetSettingsSerializer());
  }

  private static class CompilerSettingsSerializer extends JpsProjectExtensionSerializer {
    private static final String COMPILER_SETTINGS_COMPONENT_NAME = "ScalacSettings";
    private static final String COMPILER_SETTINGS_FILE = "scala_compiler.xml";

    public CompilerSettingsSerializer() {
      super(COMPILER_SETTINGS_FILE, COMPILER_SETTINGS_COMPONENT_NAME);
    }

    @Override
    public void loadExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      ProjectSettingsImpl.State state = XmlSerializer.deserialize(componentTag, ProjectSettingsImpl.State.class);
      ProjectSettingsImpl settings = new ProjectSettingsImpl(state == null ? new ProjectSettingsImpl.State() : state);
      SettingsManager.setCompilerSettings(jpsProject, settings);
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
