package org.jetbrains.plugins.scala.project.converter

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext}
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element

import java.nio.file.Path
import scala.xml.Elem

class ScalaProjectSettings(basePackages: Seq[String]) extends XmlConversion {
  def createOrUpdateIn(context: ConversionContext): Option[Path] = {
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

  def getFilesToUpdate(context: ConversionContext): Set[Path] = {
    if (basePackages.isEmpty) return Set.empty

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        val file = getDirectorySettingsFileIn(context)
        if (file.toFile.exists()) Set(file) else Set.empty
      case StorageScheme.DEFAULT =>
        Set(context.getProjectFile)
    }
  }

  private def addDirectoryBasedOptions(options: Elem, context: ConversionContext): Option[Path] = {
    val file = getDirectorySettingsFileIn(context).toFile

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
      Some(file.toPath)
    }
  }

  private def getDirectorySettingsFileIn(context: ConversionContext): Path = {
    val base = Option(context.getSettingsBaseDir).getOrElse(
      throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    base.resolve("scala_settings.xml")
  }

  private def addProjectBasedOptions(options: Elem, context: ConversionContext): Unit = {
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

  private def createOptionsElement(basePackages: Iterable[String]): Elem = {
    <option name="basePackages">
      <list>
        {basePackages.map(name => <option value={name} />)}
      </list>
    </option>
  }
}
