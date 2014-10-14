package org.jetbrains.plugins.scala

import java.io.File
import scala.annotation.tailrec
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.{ModuleUtilCore, ModuleManager, Module}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, LibraryEx}
import com.intellij.openapi.roots.{OrderRootType, ProjectFileIndex, LibraryOrderEntry, ModuleRootManager}
import com.intellij.openapi.roots.ui.configuration.libraryEditor.{NewLibraryEditor, ExistingLibraryEditor}
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore}
import scala.util.matching.Regex
import extensions._

/**
 * @author Pavel Fatin
 */
package object project {
  implicit class LibraryExt(library: Library) {
    def isScalaSdk: Boolean = libraryEx.getKind == ScalaLibraryKind

    def scalaVersion: Option[String] = LibraryVersion.findFirstIn(library.getName)

    def scalaCompilerClasspath: Option[Seq[File]] = scalaProperties.map(_.compilerClasspath)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = scalaProperties.map(_.languageLevel)

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

  implicit class ModuleExt(module: Module) {
    def hasScala: Boolean = scalaSdk.isDefined

    def scalaSdk: Option[ScalaSdk] = libraries.find(_.isScalaSdk).map(new ScalaSdk(_))

    def libraries = inReadAction {
      ModuleRootManager.getInstance(module).getOrderEntries.collect {
        case entry: LibraryOrderEntry if entry.getLibrary != null => entry.getLibrary
      }
    }

    def attach(library: Library) {
      val model = ModuleRootManager.getInstance(module).getModifiableModel
      model.addLibraryEntry(library)
      model.commit()
    }

    def detach(library: Library) {
      val model = ModuleRootManager.getInstance(module).getModifiableModel
      val entry = model.findLibraryOrderEntry(library)
      model.removeOrderEntry(entry)
      model.commit()
    }
  }
  
  implicit class ProjectExt(project: Project) {
    def hasScala: Boolean = ModuleManager.getInstance(project).getModules.exists(_.hasScala)

    def modulesWithScala: Seq[Module] = ModuleManager.getInstance(project).getModules.filter(_.hasScala)

    def scalaModules: Seq[ScalaModule] = modulesWithScala.map(new ScalaModule(_))

    def anyScalaModule: Option[ScalaModule] = scalaModules.headOption

    def scalaEvents: ScalaProjectEvents = project.getComponent(classOf[ScalaProjectEvents])

    def scalaCompilerSettigns: ScalaCompilerSettings = ScalaCompilerSettings.instanceIn(project)

    def libraries: Seq[Library] = ProjectLibraryTable.getInstance(project).getLibraries.toSeq

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
  }

  class ScalaModule(val module: Module) {
    def sdk: ScalaSdk = module.scalaSdk.map(new ScalaSdk(_)).getOrElse {
      throw new IllegalStateException("Module has no Scala SDK: " + module.getName)
    }
  }

  object ScalaModule {
    implicit def toModule(v: ScalaModule): Module = v.module
  }

  class ScalaSdk(val library: Library) {
    private def properties: ScalaLibraryProperties = library.scalaProperties.getOrElse {
      throw new IllegalStateException("Library is not Scala SDK: " + library.getName)
    }

    def compilerVersion: Option[String] = LibraryVersion.findFirstIn(library.getName)

    def compilerClasspath: Seq[File] = properties.compilerClasspath

    def languageLevel: ScalaLanguageLevel = properties.languageLevel
  }

  object ScalaSdk {
    implicit def toLibrary(v: ScalaSdk): Library = v.library
  }

  implicit class ProjectPsiElementExt(element: PsiElement) {
    def isInScalaModule: Boolean = Option(ModuleUtilCore.findModuleForPsiElement(element)).exists(_.hasScala)

    @deprecated("legacy code")
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
      if (file == null || file.getVirtualFile == null) return ScalaLanguageLevel.getDefault
      val module: Module = ProjectFileIndex.SERVICE.getInstance(element.getProject).getModuleForFile(file.getVirtualFile)
      if (module == null) return ScalaLanguageLevel.getDefault
      module.scalaSdk.map(_.languageLevel).getOrElse(ScalaLanguageLevel.getDefault)
    }

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = {
      Option(ModuleUtilCore.findModuleForPsiElement(element)).flatMap(_.scalaSdk.map(_.languageLevel))
    }
  }

  val LibraryVersion: Regex = """(?<=:|-)\d+\.\d+\.\d+\S*$""".r

  val JarVersion: Regex = """(?<=-)\d+\.\d+\.\d+\S*(?=\.jar$)""".r
}
