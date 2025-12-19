package org.jetbrains.jps.incremental.scala

import org.jdom.Element
import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadataConstants.*

import scala.jdk.CollectionConverters.CollectionHasAsScala

final case class ScalaJpsProjectMetadata(modulesWithScalaSdk: Set[String]) {
  def asXml: Element = {
    def createModuleElement(name: String): Element =
      new Element(ModuleElement).setAttribute(NameAttribute, name)

    val modulesWithScalaSdkElement = modulesWithScalaSdk.foldLeft(new Element(ModulesWithScalaSdkElement)) {
      case (element, name) =>
        element.addContent(createModuleElement(name))
    }
    new Element(RootElement).addContent(modulesWithScalaSdkElement)
  }
}

object ScalaJpsProjectMetadata {
  def parseXml(rootElement: Element): Option[ScalaJpsProjectMetadata] = {
    if (rootElement.getName != RootElement) return None
    val modulesWithScalaSdkElement = rootElement.getChild(ModulesWithScalaSdkElement)
    if (modulesWithScalaSdkElement == null) return None
    val modulesWithScalaSdk =
      modulesWithScalaSdkElement.getChildren(ModuleElement)
        .asScala
        .map(_.getAttributeValue(NameAttribute))
        .toSet
    Some(ScalaJpsProjectMetadata(modulesWithScalaSdk))
  }
}
