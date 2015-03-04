package org.jetbrains.plugins.scala.project.converter

import java.io.File

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext}
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element

import scala.xml.Elem

/**
 * @author Pavel Fatin
 */
class ScalaProjectSettings(basePackages: Seq[String]) extends XmlConversion {
  def createOrUpdateIn(context: ConversionContext): Option[File] = {
    if (basePackages.isEmpty) return None

    val optionsElement = createOptionsElement(basePackages)

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        addDirectoryBasedOptions(optionsElement, context)
      case StorageScheme.DEFAULT =>
        addProjectBasedOptions(optionsElement, context)
        None
    }
  }

  def getFilesToUpdate(context: ConversionContext): Set[File] = {
    if (basePackages.isEmpty) return Set.empty

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        val file = getDirectorySettingsFileIn(context)
        if (file.exists()) Set(file) else Set.empty
      case StorageScheme.DEFAULT =>
        Set(context.getProjectFile)
    }
  }

  private def addDirectoryBasedOptions(options: Elem, context: ConversionContext): Option[File] = {
    val file = getDirectorySettingsFileIn(context)

    if (file.exists()) {
      val rootElement = parseXml(FileUtil.loadFile(file))
      val componentElement = Option(rootElement.getChild("component")).getOrElse {
        val element = new Element("component")
        element.setAttribute("name", "ScalaProjectSettings")
        rootElement.addContent(element)
        element
      }
      componentElement.addContent(asJava(options))
      Files.write(formatXml(rootElement).getBytes, file)
      None
    } else {
      val componentElement = createSettingsElement(options)
      Files.write(formatXml(componentElement).getBytes, file)
      Some(file)
    }
  }

  private def getDirectorySettingsFileIn(context: ConversionContext): File = {
    val base = Option(context.getSettingsBaseDir).getOrElse(
      throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    new File(base, "scala_settings.xml")
  }

  private def addProjectBasedOptions(options: Elem, context: ConversionContext) {
    val rootElement = context.getProjectSettings.getRootElement
    rootElement.addContent(asJava(options))
  }

  def createSettingsElement(options: Elem): Elem = {
    <project version="4">
      <component name="ScalaProjectSettings">
        {options}
      </component>
    </project>
  }

  private def createOptionsElement(basePackages: Seq[String]): Elem = {
    <option name="basePackages">
      <list>
        {basePackages.map(name => <option value={name} />)}
      </list>
    </option>
  }
}
