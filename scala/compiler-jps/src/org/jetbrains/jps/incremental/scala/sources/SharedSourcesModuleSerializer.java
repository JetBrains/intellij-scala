package org.jetbrains.jps.incremental.scala.sources;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import scala.jdk.CollectionConverters;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SharedSourcesModuleSerializer extends JpsModulePropertiesSerializer<JpsDummyElement> {
  public SharedSourcesModuleSerializer() {
    super(SharedSourcesModuleType.INSTANCE, "SHARED_SOURCES_MODULE", "SharedSourcesOwnerModules");
  }

  @Override
  public JpsDummyElement loadProperties(@Nullable Element componentElement) {

    List<Element> options = Optional.ofNullable(componentElement)
            .map(element -> element.getChildren("option"))
            .orElseGet(Collections::emptyList);

    Optional<Element> ownersModuleNamesElement = options.stream().filter(element ->
            // The attribute value name must match the name of the org.jetbrains.sbt.project.settings.SharedSourcesOwnerModules.ownersModuleNames field
            element.getAttributes().stream().anyMatch(attr -> attr.getValue().equals("ownersModuleNames"))
    ).findFirst();

    Optional<Element> ownersModuleNamesList = ownersModuleNamesElement.map(element -> element.getChild("list"));
    Optional<List<Element>> moduleNamesOptions = ownersModuleNamesList.map(x -> x.getChildren("option"));

    Optional<List<String>> moduleNames = moduleNamesOptions
            .map(mn -> mn.stream()
                    .map(t -> t.getAttributeValue("value"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

    return (moduleNames.isPresent()) ? new SharedSourcesProperties(CollectionConverters.ListHasAsScala(moduleNames.get()).asScala().toSeq()) :
            JpsElementFactory.getInstance().createDummyElement();
  }
}
