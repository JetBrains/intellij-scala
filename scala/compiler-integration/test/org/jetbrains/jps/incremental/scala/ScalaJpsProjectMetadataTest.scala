package org.jetbrains.jps.incremental.scala

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalaJpsProjectMetadataTest {
  @Test
  def serialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedXml = generateXmlStructure(moduleNames)
    val expectedXmlString = JDOMUtil.write(expectedXml, "\n")

    val actualXml = ScalaJpsProjectMetadata(moduleNames).asXml
    val actualXmlString = JDOMUtil.write(actualXml, "\n")

    assertEquals(expectedXmlString, actualXmlString)
  }

  @Test
  def deserialization(): Unit = {
    val moduleNames = generateModuleNames
    val expectedMetadata = ScalaJpsProjectMetadata(moduleNames)

    val startingXml = generateXmlStructure(moduleNames)
    val actualMetadata = ScalaJpsProjectMetadata.parseXml(startingXml)

    assertEquals(Some(expectedMetadata), actualMetadata)
  }

  @Test
  def serializationDeserialization(): Unit = {
    val modulesWithScalaSdk =
      (1 to 10).flatMap(idx => Seq(s"module$idx.main", s"module$idx.test")).toSet
    val metadata = ScalaJpsProjectMetadata(modulesWithScalaSdk)

    val serialized = metadata.asXml
    val deserialized = ScalaJpsProjectMetadata.parseXml(serialized)

    assertEquals(Some(metadata), deserialized)
  }

  private def generateXmlStructure(moduleNames: Set[String]): Element = {
    import ScalaJpsProjectMetadataConstants._
    val modulesWithScalaSdkElement =
      moduleNames.foldLeft(new Element(ModulesWithScalaSdkElement)) { case (element, name) =>
        element.addContent(new Element(ModuleElement).setAttribute(NameAttribute, name))
      }
    new Element(RootElement).addContent(modulesWithScalaSdkElement)
  }

  private def generateModuleNames: Set[String] =
    (1 to 10).flatMap(idx => Seq(s"module$idx.main", s"module$idx.test")).toSet
}
