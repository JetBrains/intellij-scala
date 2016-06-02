package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.{LibraryEx, ProjectLibraryTable}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.libraryEditor.{ExistingLibraryEditor, NewLibraryEditor}
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore}
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.CommonProcessors.CollectProcessor
import org.jetbrains.plugins.dotty.lang.DottyTokenSets
import org.jetbrains.plugins.dotty.lang.psi.types.DottyTypeSystem
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ElementTypes
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.{ScalaTokenSets, TokenSets}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.matching.Regex

/**
 * @author Pavel Fatin
 */
package object project {
  implicit class LibraryExt(val library: Library) extends AnyVal {
    def isScalaSdk: Boolean = libraryEx.getKind.isInstanceOf[ScalaLibraryKind]

    def scalaVersion: Option[Version] = LibraryVersion.findFirstIn(library.getName).map(Version(_))

    def scalaLanguageLevel: Option[ScalaLanguageLevel] =
      scalaVersion.flatMap(_.toLanguageLevel)

    private[project] def scalaProperties: Option[ScalaLibraryProperties] =
      libraryEx.getProperties.asOptionOf[ScalaLibraryProperties]

    private def libraryEx = library.asInstanceOf[LibraryEx]

    def convertToScalaSdkWith(languageLevel: ScalaLanguageLevel, compilerClasspath: Seq[File]): ScalaSdk = {
      val properties = new ScalaLibraryProperties()
      properties.languageLevel = languageLevel
      properties.compilerClasspath = compilerClasspath

      val editor = new ExistingLibraryEditor(library, null)
      editor.setType(ScalaLibraryType.instance)
      editor.setProperties(properties)
      editor.commit()

      new ScalaSdk(library)
    }

    def classes: Set[File] = library.getFiles(OrderRootType.CLASSES).toSet.map(VfsUtilCore.virtualToIoFile)
  }

  implicit class ModuleExt(val module: Module) extends AnyVal {
    def hasScala: Boolean = scalaSdk.isDefined

    def hasDotty: Boolean = scalaSdk.isDefined && scalaSdk.get.isDottySdk

    def scalaSdk: Option[ScalaSdk] = ScalaSdkCache.instanceIn(module.getProject).get(module)

    def libraries: Set[Library] = {
      val collector = new CollectProcessor[Library]()
      OrderEnumerator.orderEntries(module).librariesOnly().forEachLibrary(collector)
      collector.getResults.asScala.toSet
    }

    def scalaLibraries: Set[Library] =
      libraries.filter(_.getName.contains(ScalaLibraryName))

    def attach(library: Library) {
      val model = ModuleRootManager.getInstance(module).getModifiableModel
      model.addLibraryEntry(library)
      model.commit()
    }

    def attach(libraries: Seq[Library]) = {
      val model = ModuleRootManager.getInstance(module).getModifiableModel
      libraries.foreach(model.addLibraryEntry)
      model.commit()
    }

    def detach(library: Library) {
      val model = ModuleRootManager.getInstance(module).getModifiableModel
      val entry = model.findLibraryOrderEntry(library)
      model.removeOrderEntry(entry)
      model.commit()
    }

    def createLibraryFromJar(urls: Seq[String], name: String): Library = {
      val lib = ProjectLibraryTable.getInstance(module.getProject).createLibrary(name)
      val model = lib.getModifiableModel
      urls.foreach(url => model.addRoot(url, OrderRootType.CLASSES))
      model.commit()
      lib
    }

    def scalaCompilerSettings: ScalaCompilerSettings = compilerConfiguration.getSettingsForModule(module)

    def configureScalaCompilerSettingsFrom(source: String, options: Seq[String]) {
      compilerConfiguration.configureSettingsForModule(module, source, options)
    }

    private def compilerConfiguration = ScalaCompilerConfiguration.instanceIn(module.getProject)
  }

  implicit class ProjectExt(val project: Project) extends AnyVal {
    private def modules: Seq[Module] = ModuleManager.getInstance(project).getModules.toSeq

    def hasScala: Boolean = modules.exists(_.hasScala)

    def hasDotty: Boolean = modulesWithScala.exists(_.hasDotty)

    def modulesWithScala: Seq[Module] = modules.filter(_.hasScala)

    def scalaModules: Seq[ScalaModule] = modulesWithScala.map(new ScalaModule(_))

    def anyScalaModule: Option[ScalaModule] = modules.find(_.hasScala).map(new ScalaModule(_))

    def scalaEvents: ScalaProjectEvents = project.getComponent(classOf[ScalaProjectEvents])

    def libraries: Seq[Library] = ProjectLibraryTable.getInstance(project).getLibraries.toSeq

    def scalaLibraries: Seq[Library] = project.libraries.filter(_.getName.contains(ScalaLibraryName))

    def scalaSdks: Seq[ScalaSdk] = libraries.filter(_.isScalaSdk).map(new ScalaSdk(_))

    def createScalaSdk(name: String, classes: Seq[File], sources: Seq[File], docs: Seq[File], compilerClasspath: Seq[File], languageLevel: ScalaLanguageLevel): ScalaSdk = {
      val library = ProjectLibraryTable.getInstance(project).createLibrary(name)

      val editor = new NewLibraryEditor()

      def addRoots(files: Seq[File], rootType: OrderRootType) {
        files.foreach(file => editor.addRoot(VfsUtil.findFileByIoFile(file, false), rootType))
      }

      addRoots(classes, OrderRootType.CLASSES)
      addRoots(sources, OrderRootType.SOURCES)
      addRoots(docs, OrderRootType.DOCUMENTATION)

      val properties = new ScalaLibraryProperties()
      properties.compilerClasspath = compilerClasspath
      properties.languageLevel = languageLevel

      editor.setType(ScalaLibraryType.instance)
      editor.setProperties(properties)

      val libraryModel = library.getModifiableModel
      editor.applyTo(libraryModel.asInstanceOf[LibraryEx.ModifiableModelEx])
      libraryModel.commit()

      new ScalaSdk(library)
    }

    def remove(library: Library) {
      ProjectLibraryTable.getInstance(project).removeLibrary(library)
    }

    def typeSystem: TypeSystem = if (hasDotty) DottyTypeSystem else ScalaTypeSystem

    def tokenSets: TokenSets = if (hasDotty) DottyTokenSets else ScalaTokenSets

    def elementTypes: ElementTypes = tokenSets.elementTypes
  }

  def typeSystemIn(project: Project): TypeSystem = project.typeSystem

  def elementTypesIn(project: Project): ElementTypes = project.elementTypes

  class ScalaModule(val module: Module) {
    def sdk: ScalaSdk = module.scalaSdk.map(new ScalaSdk(_)).getOrElse {
      throw new IllegalStateException("Module has no Scala SDK: " + module.getName)
    }
  }

  object ScalaModule {
    implicit def toModule(v: ScalaModule): Module = v.module
  }

  class ScalaSdk(val library: Library) {
    //too many instances of anonymous functions was created on getOrElse()
    private def properties: ScalaLibraryProperties = library.scalaProperties match {
      case Some(p) => p
      case None => throw new IllegalStateException("Library is not Scala SDK: " + library.getName)
    }

    def compilerVersion: Option[String] = LibraryVersion.findFirstIn(library.getName)

    def compilerClasspath: Seq[File] = properties.compilerClasspath

    def languageLevel: ScalaLanguageLevel = properties.languageLevel

    def isDottySdk: Boolean = languageLevel.isDotty
  }

  object ScalaSdk {
    implicit def toLibrary(v: ScalaSdk): Library = v.library

    def documentationUrlFor(version: Option[Version]): String =
      "http://www.scala-lang.org/api/" + version.map(_.number).getOrElse("current") + "/"
  }

  implicit class ProjectPsiElementExt(val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(element))

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    @deprecated("legacy code, use scalaLanguageLevelOrDefault", "14.10.14")
    def languageLevel: ScalaLanguageLevel = {
      @tailrec
      def getContainingFileByContext(element: PsiElement): PsiFile = {
        element match {
          case file: PsiFile => file
          case null => null
          case elem => getContainingFileByContext(elem.getContext)
        }
      }
      val file: PsiFile = getContainingFileByContext(element)
      if (file == null || file.getVirtualFile == null) return ScalaLanguageLevel.Default
      val module: Module = ProjectFileIndex.SERVICE.getInstance(element.getProject).getModuleForFile(file.getVirtualFile)
      if (module == null) return ScalaLanguageLevel.Default
      module.scalaSdk.map(_.languageLevel).getOrElse(ScalaLanguageLevel.Default)
    }

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = module.flatMap(_.scalaSdk.map(_.languageLevel))

    def scalaLanguageLevelOrDefault: ScalaLanguageLevel = scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
  }

  val LibraryVersion: Regex = """(?<=:|-)\d+\.\d+\.\d+[^:\s]*""".r

  val JarVersion: Regex = """(?<=-)\d+\.\d+\.\d+\S*(?=\.jar$)""".r

  val ScalaLibraryName: String = "scala-library"
}
