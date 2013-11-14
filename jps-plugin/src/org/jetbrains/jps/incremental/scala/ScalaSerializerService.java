package org.jetbrains.jps.incremental.scala;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer;

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
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new ProjectSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsLibraryPropertiesSerializer<?>> getLibraryPropertiesSerializers() {
    return Collections.singletonList(new LibraryPropertiesSerializer());
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

  private static class LibraryPropertiesSerializer extends JpsLibraryPropertiesSerializer<LibrarySettings> {
    private LibraryPropertiesSerializer() {
      super(ScalaLibraryType.getInstance(), "scala");
    }

    @Override
    public LibrarySettings loadProperties(@Nullable Element propertiesElement) {
      LibrarySettingsImpl.State state = XmlSerializer.deserialize(propertiesElement, LibrarySettingsImpl.State.class);
      return state == null ? null : new LibrarySettingsImpl(state);
    }

    @Override
    public void saveProperties(LibrarySettings properties, Element element) {
      // do nothing
    }
  }
}
