package org.jetbrains.jps.incremental.scala.sources;

import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import scala.Option;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JpsSbtModelSerializerExtension extends JpsModelSerializerExtension {

  // should be in sync with org.jetbrains.sbt.project.SbtProjectSystem.Id
  private static final String SBT_SYSTEM_ID = "SBT";
  /**
   * The attribute name that is used in <code>ModuleImlFileEntitiesSerializer#saveDependencyItem</code>
   * Remove when <a href="https://youtrack.jetbrains.com/issue/IDEA-360113/Extract-production-on-test-attribute-name-to-some-variable">IDEA-360113</a> is fixed
   */
  private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";

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
      Option<String> displayModuleName = getDisplayModuleName(rootElement);
      Option<String> type = Option.apply(rootElement.getAttributeValue("external.system.module.type"));
      JpsSbtExtensionService.getInstance().getOrCreateExtension(module, type, displayModuleName);
    }
  }

  //note: the code based on JpsGradleModelSerializationExtension#loadModuleDependencyProperties
  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    if (orderEntry.getAttributeValue(PRODUCTION_ON_TEST_ATTRIBUTE) != null) {
      JpsSbtExtensionService.getInstance().setProductionOnTestDependency(dependency, true);
    }
  }

  private Option<String> getDisplayModuleName(@NotNull Element rootElement) {
    List<Content> contents = rootElement.getContent();
    Optional<Content> displayModuleNameContent = contents.stream().filter(this::isDisplayModuleNameContent).findFirst();
    String value = displayModuleNameContent
            .map(t -> ((Element) t).getChild("option"))
            .map(t -> t.getAttributeValue("value"))
            .orElse(null);
    return Option.apply(value);
  }

  private Boolean isDisplayModuleNameContent(@NotNull Content content) {
    if (content instanceof Element) {
      String nameAttrValue = ((Element) content).getAttributeValue("name");
      return Objects.equals(nameAttrValue, "DisplayModuleName");
    }
    return false;
  }

}
