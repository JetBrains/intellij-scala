package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.SettingsManager;
import org.jetbrains.jps.incremental.scala.model.LibrarySettings;
import org.jetbrains.jps.incremental.scala.model.ProjectSettings;
import org.jetbrains.jps.incremental.scala.model.ScalaLibraryType;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer;
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType;
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState;

import java.util.*;

public class JpsScalaModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new GlobalSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new CompilerConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsLibraryPropertiesSerializer<?>> getLibraryPropertiesSerializers() {
    return Collections.unmodifiableList(
            Arrays.asList(new ScalaLibraryPropertiesSerializer("Scala"), new ScalaLibraryPropertiesSerializer("Dotty"))
    );
  }

  private static class GlobalSettingsSerializer extends JpsGlobalExtensionSerializer {
    private GlobalSettingsSerializer() {
      //see org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
      super("scala.xml", "ScalaSettings");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal jpsGlobal, @NotNull Element componentTag) {
      GlobalSettingsImpl.State state = XmlSerializer.deserialize(componentTag, GlobalSettingsImpl.State.class);
      GlobalSettingsImpl settings = new GlobalSettingsImpl(state);
      SettingsManager.setGlobalSettings(jpsGlobal, settings);
    }
  }

  private static class CompilerConfigurationSerializer extends JpsProjectExtensionSerializer {
    private CompilerConfigurationSerializer() {
      //see org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
      super("scala_compiler.xml", "ScalaCompilerConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      IncrementalityType incrementalityType = loadIncrementalityType(componentTag);

      CompilerSettingsImpl defaultSetting = loadSettings(componentTag);

      Map<String, String> moduleToProfile = new HashMap<>();
      Map<String, CompilerSettingsImpl> profileToSettings = new HashMap<>();

      for (Element profileElement : componentTag.getChildren("profile")) {
        String profile = profileElement.getAttributeValue("name");
        CompilerSettingsImpl settings = loadSettings(profileElement);
        profileToSettings.put(profile, settings);

        List<String> modules = StringUtil.split(profileElement.getAttributeValue("modules"), ",");
        for (String module : modules) {
          moduleToProfile.put(module, profile);
        }
      }

      ProjectSettings configuration = new ProjectSettingsImpl(incrementalityType, defaultSetting, profileToSettings, moduleToProfile);

      SettingsManager.setProjectSettings(jpsProject, configuration);
    }

    private static IncrementalityType loadIncrementalityType(Element componentTag) {
      for (Element option : componentTag.getChildren("option")) {
        if ("incrementalityType".equals(option.getAttributeValue("name"))) {
          return IncrementalityType.valueOf(option.getAttributeValue("value"));
        }
      }
      return IncrementalityType.SBT;
    }

    private static CompilerSettingsImpl loadSettings(Element componentTag) {
      ScalaCompilerSettingsState state = XmlSerializer.deserialize(componentTag, ScalaCompilerSettingsState.class);
      return new CompilerSettingsImpl(state);
    }
  }

  private static class ScalaLibraryPropertiesSerializer extends JpsLibraryPropertiesSerializer<LibrarySettings> {
    private ScalaLibraryPropertiesSerializer(String typeId) {
      super(ScalaLibraryType.getInstance(), typeId);
    }

    @Override
    public LibrarySettings loadProperties(@Nullable Element propertiesElement) {
      LibrarySettingsImpl.State state = propertiesElement == null? null :
          XmlSerializer.deserialize(propertiesElement, LibrarySettingsImpl.State.class);
      return new LibrarySettingsImpl(state == null? new LibrarySettingsImpl.State() : state);
    }
  }
}
