package org.jetbrains.jps.incremental.scala

import org.jdom.Element
import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadataConstants.*
import spray.json.DefaultJsonProtocol.{jsonFormat2, given}
import spray.json.{JsValue, JsonFormat, given}

import scala.jdk.CollectionConverters.CollectionHasAsScala

final case class ScalaJpsProjectMetadata(modulesWithScalaSdk: Set[String], useModuleDisplayName: Boolean):
  def asXml: Element =
    def createModuleElement(name: String): Element =
      new Element(ModuleElement).setAttribute(NameAttribute, name)

    val modulesWithScalaSdkElement =
      modulesWithScalaSdk.foldLeft(new Element(ModulesWithScalaSdkElement)): (element, name) =>
        element.addContent(createModuleElement(name))

    val useModuleDisplayNameElement =
      new Element(UseModuleDisplayNameElement).setAttribute(ValueAttribute, useModuleDisplayName.toString)

    new Element(RootElement).addContent(modulesWithScalaSdkElement).addContent(useModuleDisplayNameElement)
  end asXml

  def asJson: JsValue =
    summon[JsonFormat[ScalaJpsProjectMetadata]].write(this)

  /**
   * To be used when serializing project metadata to be sent as arguments to the JPS build process.
   */
  def asCompactJsonString: String = asJson.compactPrint

object ScalaJpsProjectMetadata:
  def empty: ScalaJpsProjectMetadata =
    ScalaJpsProjectMetadata(modulesWithScalaSdk = Set.empty, useModuleDisplayName = false)

  def parseXml(rootElement: Element): Option[ScalaJpsProjectMetadata] =
    if (rootElement.getName != RootElement) return None

    val modulesWithScalaSdkElement = rootElement.getChild(ModulesWithScalaSdkElement)
    if (modulesWithScalaSdkElement == null) return None
    val modulesWithScalaSdk =
      modulesWithScalaSdkElement.getChildren(ModuleElement)
        .asScala
        .map(_.getAttributeValue(NameAttribute))
        .toSet

    val useModuleDisplayNameElement = rootElement.getChild(UseModuleDisplayNameElement)
    if (useModuleDisplayNameElement == null) return None
    val attributeValue = useModuleDisplayNameElement.getAttributeValue(ValueAttribute)
    if (attributeValue == null) return None
    val useModuleDisplayName = attributeValue.toBooleanOption.getOrElse(false)

    Some(ScalaJpsProjectMetadata(modulesWithScalaSdk, useModuleDisplayName))
  end parseXml

  private given JsonFormat[ScalaJpsProjectMetadata] = jsonFormat2(ScalaJpsProjectMetadata.apply)

  def parseJson(json: JsValue): ScalaJpsProjectMetadata =
    summon[JsonFormat[ScalaJpsProjectMetadata]].read(json)

  /**
   * To be used when deserializing project metadata be sent as arguments to the JPS build process.
   */
  def parseCompactJsonString(json: String): ScalaJpsProjectMetadata =
    parseJson(json.parseJson)
