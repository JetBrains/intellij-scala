package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.lang.Language
import com.intellij.openapi.module.{ModifiableModuleModel, Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.{LibraryEx, ProjectLibraryTable}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.CommonProcessors.CollectProcessor
import org.jetbrains.plugins.dotty.DottyLanguage
import org.jetbrains.plugins.dotty.lang.psi.types.DottyTypeSystem
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.{Scala_2_11, Scala_2_13}
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
    def isScalaSdk: Boolean = libraryEx.getKind.isInstanceOf[ScalaLibraryKind.type]

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
    def hasScala: Boolean =
      scalaSdk.isDefined

    def hasDotty: Boolean =
      scalaSdk.exists(_.platform == Platform.Dotty)

    def scalaSdk: Option[ScalaSdk] =
      ScalaSdkCache.instanceIn(module.getProject).get(module)

    def modifiableModel: ModifiableRootModel =
      ModuleRootManager.getInstance(module).getModifiableModel

    def libraries: Set[Library] = {
      val collector = new CollectProcessor[Library]()
      OrderEnumerator.orderEntries(module)
        .librariesOnly()
        .forEachLibrary(collector)

      collector.getResults.asScala.toSet
    }

    def attach(library: Library): Unit = {
      val model = modifiableModel
      model.addLibraryEntry(library)
      model.commit()
    }

    def detach(library: Library): Unit = {
      val model = modifiableModel
      val entry = model.findLibraryOrderEntry(library)
      model.removeOrderEntry(entry)
      model.commit()
    }

    def scalaCompilerSettings: ScalaCompilerSettings =
      compilerConfiguration.getSettingsForModule(module)

    def configureScalaCompilerSettingsFrom(source: String, options: Seq[String]): Unit =
      compilerConfiguration.configureSettingsForModule(module, source, options)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = scalaSdk.map(_.languageLevel)

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def literalTypesEnabled: Boolean = scalaSdk.map(_.languageLevel).exists(_ >= ScalaLanguageLevel.Scala_2_13) ||
      compilerConfiguration.hasSettingForHighlighting(module, _.literalTypes)

    /**
      * @see https://github.com/non/kind-projector
      */
    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def kindProjectorPluginEnabled: Boolean =
      compilerConfiguration.hasSettingForHighlighting(module, _.plugins.exists(_.contains("kind-projector")))

    /**
      * Should we check if it's a Single Abstract Method?
      * In 2.11 works with -Xexperimental
      * In 2.12 works by default
      *
      * @return true if language level and flags are correct
      */
    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isSAMEnabled: Boolean = scalaLanguageLevel.exists {
      case lang if lang > Scala_2_11 => true // if scalaLanguageLevel is None, we treat it as Scala 2.12
      case lang if lang == Scala_2_11 =>
        compilerConfiguration.hasSettingForHighlighting(module,
          c => c.experimental || c.additionalCompilerOptions.contains("-Xexperimental")
        )
      case _ => false
    }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isPartialUnificationEnabled: Boolean =
      scalaLanguageLevel.exists(_ >= Scala_2_13) ||
        compilerConfiguration.hasSettingForHighlighting(module, _.partialUnification)

    private def compilerConfiguration =
      ScalaCompilerConfiguration.instanceIn(module.getProject)
  }

  implicit class ProjectExt(val project: Project) extends AnyVal {

    private def manager =
      ModuleManager.getInstance(project)

    def modules: Seq[Module] =
      manager.getModules.toSeq

    def modifiableModel: ModifiableModuleModel =
      manager.getModifiableModel

    def hasScala: Boolean = modulesWithScala.nonEmpty

    def hasDotty: Boolean = {
      val cached = project.getUserData(CachesUtil.PROJECT_HAS_DOTTY_KEY)
      if (cached != null) cached
      else {
        val result = modulesWithScala.exists(_.hasDotty)
        if (project.isInitialized) {
          project.putUserData(CachesUtil.PROJECT_HAS_DOTTY_KEY, java.lang.Boolean.valueOf(result))
        }
        result
      }
    }

    @CachedInUserData(project, ProjectRootManager.getInstance(project))
    def modulesWithScala: Seq[Module] =
      modules.filter(_.hasScala)

    def scalaModules: Seq[ScalaModule] =
      modulesWithScala.map(new ScalaModule(_))

    def anyScalaModule: Option[ScalaModule] =
      modulesWithScala.headOption.map(new ScalaModule(_))

    def scalaEvents: ScalaProjectEvents =
      project.getComponent(classOf[ScalaProjectEvents])

    def libraries: Seq[Library] =
      ProjectLibraryTable.getInstance(project).getLibraries.toSeq

    def typeSystem: TypeSystem = {
      if (project.hasDotty) DottyTypeSystem.instance(project)
      else ScalaTypeSystem.instance(project)
    }

    def language: Language =
      if (project.hasDotty) DottyLanguage.INSTANCE else ScalaLanguage.INSTANCE

    def isPartialUnificationEnabled: Boolean = modulesWithScala.exists(_.isPartialUnificationEnabled)
  }

  implicit class UserDataHolderExt(val holder: UserDataHolder) extends AnyVal {
    def getOrUpdateUserData[T](key: Key[T], update: => T): T = {
      Option(holder.getUserData(key)).getOrElse {
        val newValue = update
        holder match {
          case ex: UserDataHolderEx =>
            ex.putUserDataIfAbsent(key, newValue)
          case _ =>
            holder.putUserData(key, newValue)
            newValue
        }
      }
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
    //too many instances of anonymous functions was created on getOrElse()
    private def properties: ScalaLibraryProperties = library.scalaProperties match {
      case Some(p) => p
      case None => throw new IllegalStateException("Library is not Scala SDK: " + library.getName)
    }

    def compilerVersion: Option[String] = LibraryVersion.findFirstIn(library.getName)

    def compilerClasspath: Seq[File] = properties.compilerClasspath

    def platform: Platform = properties.platform

    def languageLevel: ScalaLanguageLevel = properties.languageLevel
  }

  object ScalaSdk {
    implicit def toLibrary(v: ScalaSdk): Library = v.library

    def documentationUrlFor(version: Option[Version]): String =
      "http://www.scala-lang.org/api/" + version.map(_.presentation).getOrElse("current") + "/"
  }

  implicit class ProjectPsiElementExt(val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(element))

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    def isInDottyModule: Boolean = module.exists(_.hasDotty)

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

    def kindProjectorPluginEnabled: Boolean = inThisModuleOrProject(_.kindProjectorPluginEnabled)
    def isSAMEnabled              : Boolean = inThisModuleOrProject(_.isSAMEnabled)
    def literalTypesEnabled       : Boolean = inThisModuleOrProject(_.literalTypesEnabled)
    def partialUnificationEnabled : Boolean = inThisModuleOrProject(_.isPartialUnificationEnabled)

    private def inThisModuleOrProject(predicate: Module => Boolean): Boolean = module match {
      case Some(m) => predicate(m)
      case None    => element.getProject.modulesWithScala.exists(predicate)
    }
  }

  val LibraryVersion: Regex = """(?<=:|-)\d+\.\d+\.\d+[^:\s]*""".r

  val JarVersion: Regex = """(?<=-)\d+\.\d+\.\d+\S*(?=\.jar$)""".r

  val ScalaLibraryName: String = "scala-library"

  val DottyLibraryName: String = "dotty-library"
}
