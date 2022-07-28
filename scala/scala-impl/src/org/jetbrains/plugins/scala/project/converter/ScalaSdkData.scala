package org.jetbrains.plugins.scala
package project.converter

import com.google.common.io.Files
import com.intellij.conversion.{CannotConvertException, ConversionContext, ModuleSettings}
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.util.JDOMUtil
import org.jdom.xpath.XPath
import org.jdom.{Attribute, Element}
import org.jetbrains.plugins.scala.project.converter.ScalaSdkData._

import java.io.File
import java.nio.file.Path
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.xml.{Elem, PrettyPrinter}

private case class ScalaSdkData(name: String, standardLibrary: LibraryData, languageLevel: String, compilerClasspath: Seq[String]) {
  def isEquivalentTo(compilerLibrary: LibraryData): Boolean =
    compilerClasspath.toSet == compilerLibrary.classesAsFileUrls.toSet

  def addReferenceTo(module: ModuleSettings): Unit = {
    val id = LibraryReference(ProjectLevel, name)
    id.addTo(module)
  }
  
  def createIn(context: ConversionContext): Option[Path] = {
    val libraryElement = createLibraryElement()

    context.getStorageScheme match {
      case StorageScheme.DIRECTORY_BASED =>
        Some(addDirectoryBasedLibrary(libraryElement, context))
      case StorageScheme.DEFAULT =>
        addProjectBasedLibrary(libraryElement, context)
        None
    }
  }

  private def addDirectoryBasedLibrary(library: Elem, context: ConversionContext): Path = {
    val file = {
      val fileName = name.replaceAll("\\W", "_")
      suggestLibraryFile(fileName, context)
    }
    val componentElement = <component name="libraryTable"> {library} </component>
    Files.write(formatXml(componentElement).getBytes, file)
    file.toPath
  }

  private def addProjectBasedLibrary(library: Elem, context: ConversionContext): Unit = {
    val libraryTableElement = {
      val rootElement = context.getProjectSettings.getRootElement
      XPath.selectSingleNode(rootElement, "component[@name='libraryTable']").asInstanceOf[Element]: @nowarn("cat=deprecation")
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
    ("2.11", "Scala_2_11"),
    ("2.12", "Scala_2_12"))

  private val DefaultLanguageLevel = "Scala_2_12"

  def apply(element: Element): ScalaSdkData = {
    val standardLibrary = LibraryData(element)

    @nowarn("cat=deprecation")
    val compilerClasspath = XPath.selectNodes(element, "properties/compiler-classpath/root/@url").asScala
      .iterator
      .map(_.asInstanceOf[Attribute].getValue)
      .toSeq

    val languageLevel = languageLevelFrom(compilerClasspath)
    
    ScalaSdkData(standardLibrary.name, standardLibrary, languageLevel, compilerClasspath)
  }

  def languageLevelFrom(compilerClasspath: Iterable[String]): String = {
    val compilerJarVersions = compilerClasspath.flatMap(path => versionOf(new File(path)).toSeq)

    compilerJarVersions.headOption.flatMap(languageLevelFrom).getOrElse(DefaultLanguageLevel)
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
    JDOMUtil.load(xml)
  }

  private def suggestLibraryFile(name: String, context: ConversionContext): File = {
    val base = Option(context.getSettingsBaseDir)
            .getOrElse(throw new CannotConvertException("Only directory-based IDEA projects are supported"))

    val candidates = {
      val suffixes = Iterator.single("") ++ Iterator.from(2).map("_" + _.toString)
      suffixes.map(suffix => base.resolve("libraries").resolve(s"$name$suffix.xml").toFile)
    }

    candidates.find(!_.exists).getOrElse(throw new IllegalStateException("Run out of integer numbers :)"))
  }
}
