package org.jetbrains.plugins.scala
package project.converter

import java.io.File
import java.nio.charset.Charset

import com.google.common.io.Files
import com.intellij.conversion.{ConversionContext, ModuleSettings}
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.vfs.VfsUtil
import org.jdom.Element
import org.jdom.xpath.XPath
import org.jetbrains.plugins.scala.extensions._

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
private case class LibraryReference(level: Level, name: String) {
  def resolveIn(context: ConversionContext): Option[LibraryData] =
    level.librariesIn(context).find(_.name == name)

  def addTo(module: ModuleSettings): Unit = {
    rootManagerElementIn(module).addContent(createOrderEntry())
  }

  private def createOrderEntry(): Element = {
    val entry = new Element("orderEntry")
    entry.setAttribute("type", "library")
    entry.setAttribute("name", name)
    entry.setAttribute("level", level.title)
    entry
  }

  def removeFrom(module: ModuleSettings): Unit = {
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
      val files = Option(librariesDirectory.listFiles).map(_.toSeq).getOrElse(Seq.empty)
      files.filter(_.getName.endsWith(".xml"))
    }

    libraryFiles.find { file =>
      val lines = Files.readLines(file, Charset.defaultCharset())
      lines.get(1).contains("name=\"%s\"".format(name))
    }
  }

  def deleteIn(context: ConversionContext): Unit = {
    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED => deleteDirectoryBasedLibrary(context)
      case StorageScheme.DEFAULT => deleteProjectBasedLibrary(context)
    }
  }

  private def deleteDirectoryBasedLibrary(context: ConversionContext): File = {
      val libraryFile = directoryBasedLibraryFileIn(context).getOrElse(
      throw new IllegalArgumentException(s"Cannot delete project library: $name"))

    // We have to resort to this workaround because IDEA's converter "restores" the file otherwise
    invokeLater {
      inWriteAction {
        VfsUtil.findFileByIoFile(libraryFile, true).delete(this)
      }
    }

    libraryFile
  }

  private def deleteProjectBasedLibrary(context: ConversionContext): Unit = {
    val libraryElement = {
      val rootElement = context.getProjectSettings.getRootElement
      XPath.selectSingleNode(rootElement,
        s"component[@name='libraryTable']/library[@name='$name']").asInstanceOf[Element]
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
