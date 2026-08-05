package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadataConstants.UseModuleDisplayNameElement
import org.junit.Assert.assertEquals
import org.junit.Test
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{JsArray, JsBoolean, JsObject, JsValue, enrichAny}

class ScalaJpsProjectMetadataTest {
  @Test
  def xmlSerialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedXml = generateXmlStructure(moduleNames, useModuleDisplayName = false)
    val expectedXmlString = JDOMUtil.write(expectedXml, "\n")

    val actualXml = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = false).asXml
    val actualXmlString = JDOMUtil.write(actualXml, "\n")

    assertEquals(expectedXmlString, actualXmlString)
  }

  @Test
  def xmlDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedMetadata = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = true)

    val startingXml = generateXmlStructure(moduleNames, useModuleDisplayName = true)
    val actualMetadata = ScalaJpsProjectMetadata.parseXml(startingXml)

    assertEquals(Some(expectedMetadata), actualMetadata)
  }

  @Test
  def xmlSerializationDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = true)

    val serialized = metadata.asXml
    val deserialized = ScalaJpsProjectMetadata.parseXml(serialized)

    assertEquals(Some(metadata), deserialized)
  }

  @Test
  def jsonSerialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = true)

    val expectedJson = generateJson(moduleNames, useModuleDisplayName = true)
    val actualJson = metadata.asJson

    assertEquals(expectedJson, actualJson)
  }

  @Test
  def jsonDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedMetadata = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = false)

    val startingJson = generateJson(moduleNames, useModuleDisplayName = false)
    val actualMetadata = ScalaJpsProjectMetadata.parseJson(startingJson)

    assertEquals(expectedMetadata, actualMetadata)
  }

  @Test
  def jsonSerializationDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames, useModuleDisplayName = true)

    val serialized = metadata.asJson
    val deserialized = ScalaJpsProjectMetadata.parseJson(serialized)

    assertEquals(metadata, deserialized)
  }

  private def generateXmlStructure(moduleNames: Set[String], useModuleDisplayName: Boolean): Element = {
    import ScalaJpsProjectMetadataConstants._
    val modulesWithScalaSdkElement =
      moduleNames.foldLeft(new Element(ModulesWithScalaSdkElement)) { case (element, name) =>
        element.addContent(new Element(ModuleElement).setAttribute(NameAttribute, name))
      }

    val useModuleDisplayNameElement =
      new Element(UseModuleDisplayNameElement).setAttribute(ValueAttribute, useModuleDisplayName.toString)

    new Element(RootElement).addContent(modulesWithScalaSdkElement).addContent(useModuleDisplayNameElement)
  }

  private def generateJson(moduleNames: Set[String], useModuleDisplayName: Boolean): JsValue = {
    import ScalaJpsProjectMetadataConstants.ModulesWithScalaSdkElement
    JsObject(
      ModulesWithScalaSdkElement -> JsArray(moduleNames.toSeq.map(_.toJson)*),
      UseModuleDisplayNameElement -> JsBoolean(useModuleDisplayName)
    )
  }

  private def generateModuleNames: Set[String] =
    (1 to 10).flatMap(idx => Seq(s"module$idx.main", s"module$idx.test")).toSet
}
