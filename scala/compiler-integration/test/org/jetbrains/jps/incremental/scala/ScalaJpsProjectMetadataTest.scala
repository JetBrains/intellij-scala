package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Test
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{JsArray, JsObject, JsValue, enrichAny}

class ScalaJpsProjectMetadataTest {
  @Test
  def xmlSerialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedXml = generateXmlStructure(moduleNames)
    val expectedXmlString = JDOMUtil.write(expectedXml, "\n")

    val actualXml = ScalaJpsProjectMetadata(moduleNames).asXml
    val actualXmlString = JDOMUtil.write(actualXml, "\n")

    assertEquals(expectedXmlString, actualXmlString)
  }

  @Test
  def xmlDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedMetadata = ScalaJpsProjectMetadata(moduleNames)

    val startingXml = generateXmlStructure(moduleNames)
    val actualMetadata = ScalaJpsProjectMetadata.parseXml(startingXml)

    assertEquals(Some(expectedMetadata), actualMetadata)
  }

  @Test
  def xmlSerializationDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames)

    val serialized = metadata.asXml
    val deserialized = ScalaJpsProjectMetadata.parseXml(serialized)

    assertEquals(Some(metadata), deserialized)
  }

  @Test
  def jsonSerialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames)

    val expectedJson = generateJson(moduleNames)
    val actualJson = metadata.asJson

    assertEquals(expectedJson, actualJson)
  }

  @Test
  def jsonDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedMetadata = ScalaJpsProjectMetadata(moduleNames)

    val startingJson = generateJson(moduleNames)
    val actualMetadata = ScalaJpsProjectMetadata.parseJson(startingJson)

    assertEquals(expectedMetadata, actualMetadata)
  }

  @Test
  def jsonSerializationDeserialization(): Unit = {
    val moduleNames = generateModuleNames
    val metadata = ScalaJpsProjectMetadata(moduleNames)

    val serialized = metadata.asJson
    val deserialized = ScalaJpsProjectMetadata.parseJson(serialized)

    assertEquals(metadata, deserialized)
  }

  private def generateXmlStructure(moduleNames: Set[String]): Element = {
    import ScalaJpsProjectMetadataConstants._
    val modulesWithScalaSdkElement =
      moduleNames.foldLeft(new Element(ModulesWithScalaSdkElement)) { case (element, name) =>
        element.addContent(new Element(ModuleElement).setAttribute(NameAttribute, name))
      }
    new Element(RootElement).addContent(modulesWithScalaSdkElement)
  }

  private def generateJson(moduleNames: Set[String]): JsValue = {
    import ScalaJpsProjectMetadataConstants.ModulesWithScalaSdkElement
    JsObject(
      ModulesWithScalaSdkElement -> JsArray(moduleNames.toSeq.map(_.toJson): _*)
    )
  }

  private def generateModuleNames: Set[String] =
    (1 to 10).flatMap(idx => Seq(s"module$idx.main", s"module$idx.test")).toSet
}
