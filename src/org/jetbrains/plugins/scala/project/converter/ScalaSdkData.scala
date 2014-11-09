package org.jetbrains.plugins.scala
package project.converter

import java.io.{File, StringReader}

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext, ModuleSettings}
import com.intellij.openapi.components.StorageScheme
import org.jdom.input.SAXBuilder
import org.jdom.xpath.XPath
import org.jdom.{Attribute, Element}
import org.jetbrains.plugins.scala.project.converter.ScalaSdkData._

import scala.collection.JavaConverters._
import scala.xml.{Elem, PrettyPrinter}

/**
 * @author Pavel Fatin
 */
private case class ScalaSdkData(name: String, standardLibrary: LibraryData, languageLevel: String, compilerClasspath: Seq[String]) {
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
      val fileName = name.replaceAll("\\W", "_")
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
        <option name="languageLevel" value={languageLevel} />
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
  private val VersionToLanguageLevel = Seq(
    ("2.7", "Scala_2_7"),
    ("2.8", "Scala_2_8"),
    ("2.9", "Scala_2_9"),
    ("2.10", "Scala_2_10"),
    ("2.11", "Scala_2_11"))

  def findAllIn(context: ConversionContext): Seq[ScalaSdkData] = {
    val elements = context.getProjectLibrariesSettings.getProjectLibraries.asScala
    elements.filter(_.getAttributeValue("type") == "Scala").map(ScalaSdkData(_)).toSeq
  }

  def apply(element: Element): ScalaSdkData = {
    val standardLibrary = LibraryData(element)

    val compilerClasspath = XPath.selectNodes(element, "properties/compiler-classpath/root/@url").asScala
            .map(_.asInstanceOf[Attribute].getValue)

    val languageLevel = languageLevelFrom(compilerClasspath)
    
    ScalaSdkData(standardLibrary.name, standardLibrary, languageLevel, compilerClasspath)
  }

  def languageLevelFrom(compilerClasspath: Seq[String]): String = {
    val compilerJarVersions = compilerClasspath.flatMap(path => versionOf(new File(path)).toSeq)

    compilerJarVersions.headOption.flatMap(languageLevelFrom).getOrElse("Scala_2_11")
  }
  
  private def versionOf(file: File): Option[String] = {
    val FileName = "(?:scala-compiler|scala-library|scala-reflect)-(.*?)(?:-src|-sources|-javadoc).jar".r

    file.getName match {
      case FileName(number) => Some(number)
      case _ => None
    }
  }

  private def languageLevelFrom(version: String): Option[String] =
    VersionToLanguageLevel.find(p => version.startsWith(p._1)).map(_._2)

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
