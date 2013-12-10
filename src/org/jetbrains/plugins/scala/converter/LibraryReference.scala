package org.jetbrains.plugins.scala
package converter

import com.intellij.conversion.{ModuleSettings, ConversionContext}
import org.jdom.Element
import org.jdom.xpath.XPath
import collection.JavaConverters._
import com.intellij.openapi.components.StorageScheme
import java.io.File
import com.google.common.io.Files
import java.nio.charset.Charset

/**
 * @author Pavel Fatin
 */
private case class LibraryReference(level: Level, name: String) {
  def resolveIn(context: ConversionContext): Option[LibraryData] =
    level.librariesIn(context).find(_.name == name)

  def addTo(module: ModuleSettings) {
    rootManagerElementIn(module).addContent(createOrderEntry())
  }

  private def createOrderEntry(): Element = {
    val entry = new Element("orderEntry")
    entry.setAttribute("type", "library")
    entry.setAttribute("name", name)
    entry.setAttribute("level", level.title)
    entry
  }

  def removeFrom(module: ModuleSettings) {
    val element = findOrderEntryIn(module).getOrElse(throw new IllegalArgumentException(
      s"Cannot remove library (${level.title}/$name}) dependency in module ${module.getModuleName}"))

    element.detach()
  }

  private def findOrderEntryIn(module: ModuleSettings): Option[Element] = {
    val node = XPath.selectSingleNode(rootManagerElementIn(module),
      s"orderEntry[@type='library' and @name='$name' and @level='${level.title}']")

    Option(node.asInstanceOf[Element])
  }

  private def rootManagerElementIn(module: ModuleSettings): Element =
    module.getComponentElement("NewModuleRootManager")

  def libraryStorageFileIn(context: ConversionContext): Option[File] = {
    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED => directoryBasedLibraryFileIn(context)
      case StorageScheme.DEFAULT => Some(context.getProjectFile)
    }
  }

  private def directoryBasedLibraryFileIn(context: ConversionContext): Option[File] = {
    val libraryFiles = {
      val librariesDirectory = new File(context.getSettingsBaseDir, "libraries")
      librariesDirectory.listFiles.toSeq.filter(_.getName.endsWith(".xml"))
    }

    libraryFiles.find { file =>
      val lines = Files.readLines(file, Charset.defaultCharset())
      lines.get(1).contains("name=\"%s\"".format(name))
    }
  }

  def deleteIn(context: ConversionContext) {
    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED => deleteDirectoryBasedLibrary(context)
      case StorageScheme.DEFAULT => deleteProjectBasedLibrary(context)
    }
  }

  private def deleteDirectoryBasedLibrary(context: ConversionContext): File = {
    val libraryFile = directoryBasedLibraryFileIn(context).getOrElse(
      throw new IllegalArgumentException(s"Cannot delete project library: $name"))
    
    libraryFile.delete()

    libraryFile
  }

  private def deleteProjectBasedLibrary(context: ConversionContext) {
    val libraryElement = {
      val rootElement = context.getProjectSettings.getRootElement
      XPath.selectSingleNode(rootElement,
        s"component[@name='libraryTable']/library[@name=$name]").asInstanceOf[Element]
    }
    if (libraryElement == null) {
      throw new IllegalArgumentException(s"Cannot delete project library: $name")
    }
    libraryElement.detach()
  }
}

private object LibraryReference {
  def findAllIn(module: ModuleSettings): Seq[LibraryReference] = {
    val libraryEntries = module.getOrderEntries.asScala.filter(_.getAttributeValue("type") == "library")
    libraryEntries.map(LibraryReference(_))
  }

  def apply(element: Element): LibraryReference = {
    LibraryReference(Level.fromTitle(element.getAttributeValue("level")), element.getAttributeValue("name"))
  }
}
