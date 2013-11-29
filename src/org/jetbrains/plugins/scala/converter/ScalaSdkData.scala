package org.jetbrains.plugins.scala
package converter

import com.intellij.conversion.{CannotConvertException, ConversionContext, ModuleSettings}
import scala.xml.{Elem, PrettyPrinter}
import com.google.common.io.Files
import java.io.{StringReader, File}
import org.jdom.{Attribute, Element}
import org.jdom.xpath.XPath
import collection.JavaConverters._
import com.intellij.openapi.components.StorageScheme
import org.jdom.input.SAXBuilder
import ScalaSdkData._

/**
 * @author Pavel Fatin
 */
private case class ScalaSdkData(name: String, standardLibrary: LibraryData, compilerClasspath: Seq[String]) {
  def isEquivalentTo(compilerLibrary: LibraryData): Boolean =
    compilerClasspath.toSet == compilerLibrary.classesAsFileUrls.toSet

  def addReferenceTo(module: ModuleSettings) {
    val id = LibraryReference(ProjectLevel, name)
    id.addTo(module)
  }
  
  def createIn(context: ConversionContext): Option[File] = {
    val libraryElement = createLibraryElement()

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        Some(addDirectoryBasedLibrary(libraryElement, context))
      case StorageScheme.DEFAULT =>
        addProjectBasedLibrary(libraryElement, context)
        None
    }
  }

  private def addDirectoryBasedLibrary(library: Elem, context: ConversionContext): File = {
    val file = {
      val fileName = name.replace('-', '_')
      suggestLibraryFile(fileName, context)
    }
    val componentElement = <component name="libraryTable"> {library} </component>
    Files.write(formatXml(componentElement).getBytes, file)
    file
  }

  private def addProjectBasedLibrary(library: Elem, context: ConversionContext) {
    val libraryTableElement = {
      val rootElement = context.getProjectSettings.getRootElement
      XPath.selectSingleNode(rootElement, "component[@name='libraryTable']").asInstanceOf[Element]
    }
    val libraryElement = parseXml(formatXml(library))
    libraryTableElement.addContent(libraryElement)
  }

  private def createLibraryElement(): Elem = {
    <library name={name} type="Scala">
      <properties>
        <compiler-classpath>
          {compilerClasspath.map(url => <root url={url}/>)}
        </compiler-classpath>
      </properties>
      <CLASSES>
        {standardLibrary.classes.map(url => <root url={url}/>)}
      </CLASSES>
      <JAVADOC>
        {standardLibrary.docs.map(url => <root url={url}/>)}
      </JAVADOC>
      <SOURCES>
        {standardLibrary.sources.map(url => <root url={url}/>)}
      </SOURCES>
    </library>
  }
}

private object ScalaSdkData {
  def findAllIn(context: ConversionContext): Seq[ScalaSdkData] = {
    val elements = context.getProjectLibrariesSettings.getProjectLibraries.asScala
    elements.filter(_.getAttributeValue("type") == "Scala").map(ScalaSdkData(_)).toSeq
  }

  def apply(element: Element): ScalaSdkData = {
    val standardLibrary = LibraryData(element)

    val compilerClasspath = XPath.selectNodes(element, "properties/compiler-classpath/root/@url").asScala
            .map(_.asInstanceOf[Attribute].getValue)

    ScalaSdkData(standardLibrary.name, standardLibrary, compilerClasspath)
  }
  
  private def formatXml(element: Elem): String = {
    val printer = new PrettyPrinter(180, 2)
    printer.format(element)
  }

  private def parseXml(xml: String): Element = {
    val builder = new SAXBuilder()
    val document = builder.build(new StringReader(xml))
    document.detachRootElement()
  }

  private def suggestLibraryFile(name: String, context: ConversionContext): File = {
    val base = Option(context.getSettingsBaseDir)
            .getOrElse(throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    val candidates = {
      val suffixes = Iterator.single("") ++ Iterator.from(2).map("_" + _.toString)
      suffixes.map(suffix => new File(new File(base, "libraries"), s"$name$suffix.xml"))
    }

    candidates.find(!_.exists).getOrElse(throw new IllegalStateException("Run out of integer numbers :)"))
  }
}
