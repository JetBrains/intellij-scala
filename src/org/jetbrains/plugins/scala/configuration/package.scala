package org.jetbrains.plugins.scala

import java.io.File
import scala.annotation.tailrec
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.{ModuleUtilCore, ModuleManager, Module}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.{ProjectFileIndex, LibraryOrderEntry, ModuleRootManager}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import extensions._

/**
 * @author Pavel Fatin
 */
package object configuration {
  implicit class LibraryExt(library: Library) {
    def isScalaSdk: Boolean = libraryEx.getKind == ScalaLibraryKind

    def scalaCompilerClasspath: Option[Seq[File]] = scalaProperties.map(_.compilerClasspath)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = scalaProperties.map(_.languageLevel)

    private[configuration] def scalaProperties: Option[ScalaLibraryProperties] =
      libraryEx.getProperties.asOptionOf[ScalaLibraryProperties]

    private def libraryEx = library.asInstanceOf[LibraryEx]
  }

  implicit class ModuleExt(module: Module) {
    def hasScala: Boolean = scalaSdk.isDefined

    def scalaSdk: Option[ScalaSdk] = moduleLibraries.find(_.isScalaSdk).map(new ScalaSdk(_))

    private def moduleLibraries = inReadAction {
      ModuleRootManager.getInstance(module).getOrderEntries.collect {
        case entry: LibraryOrderEntry if entry.getLibrary != null => entry.getLibrary
      }
    }
  }
  
  implicit class ProjectExt(project: Project) {
    def hasScala: Boolean = ModuleManager.getInstance(project).getModules.exists(_.hasScala)

    def modulesWithScala: Seq[Module] = ModuleManager.getInstance(project).getModules.filter(_.hasScala)

    def scalaModules: Seq[ScalaModule] = modulesWithScala.map(new ScalaModule(_))

    def anyScalaModule: Option[ScalaModule] = scalaModules.headOption

    def scalaEvents: ScalaProjectEvents = project.getComponent(classOf[ScalaProjectEvents])

    def scalaSettings: ScalaSettings = new ScalaSettings(ScalaProjectSettings.getInstance(project))
    
    def scalaCompilerSettigns: ScalaCompilerSettings = ScalaCompilerSettings.instanceIn(project)
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

    def compilerClasspath: Seq[File] = properties.compilerClasspath

    def languageLevel: ScalaLanguageLevel = properties.languageLevel
  }

  object ScalaSdk {
    implicit def toLibrary(v: ScalaSdk): Library = v.library
  }

  implicit class ProjectPsiElementExt(element: PsiElement) {
    def isInScalaModule: Boolean = Option(ModuleUtilCore.findModuleForPsiElement(element)).exists(_.hasScala)

    // TODO clean this legacy code
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
  }

  class ScalaSettings(delegate: ScalaProjectSettings) {
    def basePackage: Option[String] = Option(delegate.getBasePackage).filter(!_.isEmpty)
  }
}
