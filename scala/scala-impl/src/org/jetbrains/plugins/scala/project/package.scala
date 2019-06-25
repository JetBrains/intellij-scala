package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.ProjectTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.{ModifiableModuleModel, Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.{LibraryEx, ProjectLibraryTable}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiElement
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.PathsList
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.sbt.project.module.SbtModuleType

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
 * @author Pavel Fatin
 */
package object project {

  import project.ScalaLanguageLevel._

  implicit class LibraryExt(private val library: Library) extends AnyVal {

    import LibraryExt._

    def isScalaSdk: Boolean = library match {
      case libraryEx: LibraryEx => libraryEx.isScalaSdk
      case _ => false
    }

    def compilerVersion: Option[String] = name.flatMap(LibraryVersion.findFirstIn)

    def hasRuntimeLibrary: Boolean = name.exists(isRuntimeLibrary)

    private def name: Option[String] = Option(library.getName)
  }

  object LibraryExt {

    private val LibraryVersion = "(?<=:|-)\\d+\\.\\d+\\.\\d+[^:\\s]*".r

    private[this] val RuntimeLibrary = "((?:scala|dotty)-library).+".r

    private[this] val JarVersion = "(?<=-)\\d+\\.\\d+\\.\\d+\\S*(?=\\.jar$)".r

    def isRuntimeLibrary(name: String): Boolean = RuntimeLibrary.findFirstIn(name).isDefined

    def runtimeVersion(input: String): Option[String] = JarVersion.findFirstIn(input)
  }

  implicit class LibraryExExt(private val library: LibraryEx) extends AnyVal {

    def isScalaSdk: Boolean = library.getKind == ScalaLibraryType.Kind

    def properties: ScalaLibraryProperties = library.getProperties match {
      case properties: ScalaLibraryProperties => properties
      case _ => throw new IllegalStateException("Library is not a Scala SDK: " + library.getName)
    }
  }

  implicit class ModuleExt(private val module: Module) extends AnyVal {
    def isSourceModule: Boolean = SbtModuleType.unapply(module).isEmpty

    def hasScala: Boolean =
      scalaSdk.isDefined

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def hasScala3: Boolean = scalaLanguageLevel.exists(_ >= Scala_3_0)

    def scalaSdk: Option[LibraryEx] = Option {
      ScalaSdkCache(module.getProject)(module)
    }

    def modifiableModel: ModifiableRootModel =
      ModuleRootManager.getInstance(module).getModifiableModel

    def libraries: Set[Library] = {
      val collector = new CollectProcessor[Library]()
      OrderEnumerator.orderEntries(module)
        .librariesOnly()
        .forEachLibrary(collector)

      collector.getResults.asScala.toSet
    }

    def scalaCompilerSettings: ScalaCompilerSettings =
      compilerConfiguration.getSettingsForModule(module)

    def configureScalaCompilerSettingsFrom(source: String, options: Seq[String]): Unit =
      compilerConfiguration.configureSettingsForModule(module, source, options)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = scalaSdk.map(_.properties.languageLevel)

    def scalaCompilerClasspath: Seq[File] = module.scalaSdk
      .fold(throw new IllegalArgumentException("No Scala SDK configured for module: " + module.getName)) {
        _.properties.compilerClasspath
      }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def literalTypesEnabled: Boolean = scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13) ||
      compilerConfiguration.hasSettingForHighlighting(module, _.literalTypes)

    /**
     * @see https://github.com/non/kind-projector
     */
    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def kindProjectorPluginEnabled: Boolean =
      compilerConfiguration.hasSettingForHighlighting(module, _.plugins.exists(_.contains("kind-projector")))

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def betterMonadicForPluginEnabled: Boolean =
      compilerConfiguration.hasSettingForHighlighting(module, _.plugins.exists(_.contains("better-monadic-for")))

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

  implicit class ProjectExt(private val project: Project) extends AnyVal {

    def subscribeToModuleRootChanged(parentDisposable: Disposable = project)
                                    (onRootsChanged: ModuleRootEvent => Unit): Unit =
      project.getMessageBus.connect(parentDisposable).subscribe(
        ProjectTopics.PROJECT_ROOTS,
        new ModuleRootListener {
          override def rootsChanged(event: ModuleRootEvent): Unit = onRootsChanged(event)
        }
      )

    private def manager =
      ModuleManager.getInstance(project)

    def modules: Seq[Module] =
      manager.getModules.toSeq

    def sourceModules: Seq[Module] = manager.getModules.filter(_.isSourceModule)

    def modifiableModel: ModifiableModuleModel =
      manager.getModifiableModel

    def hasScala: Boolean = modulesWithScala.nonEmpty

    @CachedInUserData(project, ProjectRootManager.getInstance(project))
    def modulesWithScala: Seq[Module] =
      modules.filter(_.hasScala)

    def anyScalaModule: Option[Module] =
      modulesWithScala.headOption

    def libraries: Seq[Library] =
      ProjectLibraryTable.getInstance(project).getLibraries.toSeq

    def baseDir: VirtualFile = LocalFileSystem.getInstance().findFileByPath(project.getBasePath)

    def isPartialUnificationEnabled: Boolean = modulesWithScala.exists(_.isPartialUnificationEnabled)
  }

  implicit class UserDataHolderExt(private val holder: UserDataHolder) extends AnyVal {
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

  implicit class ProjectPsiElementExt(private val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(element))

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    def isInScala3Module: Boolean = module.exists(_.hasScala3)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = module.flatMap(_.scalaLanguageLevel)

    def scalaLanguageLevelOrDefault: ScalaLanguageLevel = scalaLanguageLevel.getOrElse(ScalaLanguageLevel.getDefault)

    def kindProjectorPluginEnabled: Boolean = inThisModuleOrProject(_.kindProjectorPluginEnabled)

    def betterMonadicForEnabled: Boolean = inThisModuleOrProject(_.betterMonadicForPluginEnabled)

    def isSAMEnabled: Boolean = inThisModuleOrProject(_.isSAMEnabled)

    def literalTypesEnabled: Boolean = inThisModuleOrProject(_.literalTypesEnabled)

    def partialUnificationEnabled: Boolean = inThisModuleOrProject(_.isPartialUnificationEnabled)

    private def inThisModuleOrProject(predicate: Module => Boolean): Boolean = module match {
      case Some(m) => predicate(m)
      case None => element.getProject.modulesWithScala.exists(predicate)
    }
  }

  implicit class PathsListExt(private val list: PathsList) extends AnyVal {

    def addScalaClassPath(module: Module): Unit =
      try {
        val files = module.scalaCompilerClasspath.asJava
        list.addAllFiles(files)
      } catch {
        case e: IllegalArgumentException => throw new ExecutionException(e.getMessage.replace("SDK", "facet"))
      }

    def addRunners(): Unit = list.add(util.ScalaUtil.runnersPath())
  }
}
