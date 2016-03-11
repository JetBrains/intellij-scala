package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.util.text.StringUtil;
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

import java.util.*;

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

  private static class CompilerConfigurationSerializer extends JpsProjectExtensionSerializer {
    private CompilerConfigurationSerializer() {
      super("scala_compiler.xml", "ScalaCompilerConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      IncrementalityType incrementalityType = loadIncrementalityType(componentTag);

      CompilerSettingsImpl defaultSetting = loadSettings(componentTag);

      Map<String, String> moduleToProfile = new HashMap<String, String>();
      Map<String, CompilerSettingsImpl> profileToSettings = new HashMap<String, CompilerSettingsImpl>();

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
      return IncrementalityType.IDEA;
    }

    private static CompilerSettingsImpl loadSettings(Element componentTag) {
      CompilerSettingsImpl.State state = XmlSerializer.deserialize(componentTag, CompilerSettingsImpl.State.class);
      return new CompilerSettingsImpl(state == null ? new CompilerSettingsImpl.State() : state);
    }

    @Override
    public void saveExtension(@NotNull JpsProject jpsProject, @NotNull Element componentTag) {
      // do nothing
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

    @Override
    public void saveProperties(LibrarySettings properties, Element element) {
      // do nothing
    }
  }
}
