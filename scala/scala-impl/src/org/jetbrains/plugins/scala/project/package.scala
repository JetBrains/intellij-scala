package org.jetbrains.plugins.scala

import java.io.File
import java.util.jar.Attributes

import com.intellij.ProjectTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.module._
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.{LibraryEx, ProjectLibraryTable}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.JarUtil._
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{LanguageSubstitutors, PsiElement, PsiFile}
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.PathsList
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSettings

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
 * @author Pavel Fatin
 */
package object project {

  object UserDataKeys {

    // used to "attach" a module to some scala file, which is out of any module for some reason
    // the primary purpose is to attach a module for a scala scratch file
    val SCALA_ATTACHED_MODULE = new Key[Module]("ScalaAttachedModule")
  }


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

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def hasNewCollectionsFramework: Boolean = scalaLanguageLevel.exists(_ >= Scala_2_13)

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isIdBindingEnabled: Boolean = scalaLanguageLevel.exists(_ >= Scala_2_12)

    def scalaSdk: Option[LibraryEx] = Option {
      ScalaSdkCache(module.getProject)(module)
    }

    def isSharedSourceModule: Boolean = ModuleType.get(module).getId == "SHARED_SOURCES_MODULE"

    def isScalaJs: Boolean = ScalaCompilerConfiguration.hasCompilerPlugin(module, "scala-js")

    def isScalaNative: Boolean = ScalaCompilerConfiguration.hasCompilerPlugin(module, "scala-native")

    def isJvmModule: Boolean = !isScalaJs && !isScalaNative && !isSharedSourceModule

    def findJVMModule: Option[Module] = {
      if (isJvmModule) {
        Some(module)
      }
      else if (isSharedSourceModule) {
        val moduleManager = ModuleManager.getInstance(module.getProject)
        val dependents = moduleManager.getModuleDependentModules(module).asScala
        dependents.find(_.isJvmModule)
      }
      else {
        sharedSourceDependency.flatMap(_.findJVMModule)
      }
    }

    def sharedSourceDependency: Option[Module] =
      ModuleRootManager.getInstance(module).getDependencies
        .find(_.isSharedSourceModule)

    def modifiableModel: ModifiableRootModel =
      ModuleRootManager.getInstance(module).getModifiableModel

    def libraries: Set[Library] = {
      val collector = new CollectProcessor[Library]()
      OrderEnumerator.orderEntries(module)
        .librariesOnly()
        .forEachLibrary(collector)

      collector.getResults.asScala.toSet
    }

    def sbtVersion: Option[Version] =
      SbtSettings.getInstance(module.getProject)
        .getLinkedProjectSettings(module)
        .flatMap { projectSettings =>
          Option(projectSettings.sbtVersion)
        }.map {
        Version(_)
      }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isTrailingCommasEnabled: Boolean = scalaSdk.flatMap {
      _.compilerVersion
    }.map {
      Version(_)
    }.exists {
      _ >= Version("2.12.2")
    } || sbtVersion.exists {
      _ >= Version("1.0")
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
      compilerConfiguration.hasSettingForHighlighting(module) {
        _.additionalCompilerOptions.contains("-Yliteral-types")
      }

    /**
     * @see https://github.com/non/kind-projector
     */
    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def kindProjectorPluginEnabled: Boolean = kindProjectorPlugin.isDefined

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def kindProjectorPlugin: Option[String] =
      compilerConfiguration.settingsForHighlighting(module).flatMap {
        _.plugins
      }.find {
        _.contains("kind-projector")
      }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def betterMonadicForPluginEnabled: Boolean =
      compilerConfiguration.hasSettingForHighlighting(module) {
        _.plugins.exists(_.contains("better-monadic-for"))
      }

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
        compilerConfiguration.hasSettingForHighlighting(module) { settings =>
          settings.experimental || settings.additionalCompilerOptions.contains("-Xexperimental")
        }
      case _ => false
    }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isPartialUnificationEnabled: Boolean =
      scalaLanguageLevel.exists(_ >= Scala_2_13) ||
        compilerConfiguration.hasSettingForHighlighting(module) {
          _.additionalCompilerOptions.contains("-Ypartial-unification")
        }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def isMetaEnabled: Boolean =
      compilerConfiguration.hasSettingForHighlighting(module) {
        _.plugins.exists(isMetaParadiseJar)
      }

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    def customDefaultImports: Option[Seq[String]] =
      compilerConfiguration
        .settingsForHighlighting(module)
        .flatMap(_.additionalCompilerOptions)
        .reverseIterator
        .collectFirst { case RootImportSetting(imports) => imports }

    private def compilerConfiguration =
      ScalaCompilerConfiguration.instanceIn(module.getProject)

    private[this] def isMetaParadiseJar(pathname: String): Boolean = new File(pathname) match {
      case file if containsEntry(file, "scalac-plugin.xml") =>
        def hasAttribute(nameSuffix: String, value: String) = getJarAttribute(
          file,
          new Attributes.Name(s"Specification-$nameSuffix")
        ) == value

        hasAttribute("Vendor", "org.scalameta") &&
          hasAttribute("Title", "paradise")
      case _ => false
    }
  }

  private object RootImportSetting {
    private val Yimports   = "-Yimports:"
    private val Ynopredef  = "-Yno-predef"
    private val Ynoimports = "-Yno-imports"

    private[this] val importSettingsPrefixes = Seq(Yimports, Ynopredef, Ynoimports)

    def unapply(setting: String): Option[Seq[String]] = {
      val prefix = importSettingsPrefixes.find(setting.startsWith)

      prefix.collect {
        case Yimports   => setting.substring(Yimports.length).split(",").map(_.trim)
        case Ynopredef  => Seq("java.lang", "scala")
        case Ynoimports => Seq.empty
      }
    }
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

  implicit class VirtualFileExt(private val file: VirtualFile) extends AnyVal {

    def isScala3(implicit project: Project): Boolean =
      LanguageSubstitutors.getInstance.substituteLanguage(
        ScalaLanguage.INSTANCE,
        file,
        project
      ) != ScalaLanguage.INSTANCE
  }

  implicit class ProjectPsiFileExt(private val file: PsiFile) extends AnyVal {

    def isMetaEnabled: Boolean =
      !ScStubElementType.Processing &&
        !DumbService.isDumb(file.getProject) &&
        isEnabledIn(_.isMetaEnabled)

    def isTrailingCommasEnabled: Boolean = {
      import ScalaProjectSettings.TrailingCommasMode._
      ScalaProjectSettings.getInstance(file.getProject).getTrailingCommasMode match {
        case Enabled => true
        case Disabled => false
        case Auto => isEnabledIn(_.isTrailingCommasEnabled)
      }
    }

    def isIdBindingEnabled: Boolean = isEnabledIn(_.isIdBindingEnabled)

    private def isEnabledIn(predicate: Module => Boolean): Boolean =
      applicationUnitTestModeEnabled ||
        file.module.exists(predicate)
  }

  implicit class ProjectPsiElementExt(private val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(ModuleUtilCore.findModuleForPsiElement(element))

    def fileIndex: ProjectFileIndex = ProjectFileIndex.getInstance(element.getProject)

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    def isInScala3Module: Boolean = module.exists(_.hasScala3)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = module.flatMap(_.scalaLanguageLevel)

    def scalaLanguageLevelOrDefault: ScalaLanguageLevel = scalaLanguageLevel.getOrElse(ScalaLanguageLevel.getDefault)

    def kindProjectorPluginEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorPluginEnabled)
    def kindProjectorPlugin: Option[String] = inThisModuleOrProject(_.kindProjectorPlugin).flatten

    def betterMonadicForEnabled: Boolean = isDefinedInModuleOrProject(_.betterMonadicForPluginEnabled)

    def isSAMEnabled: Boolean = isDefinedInModuleOrProject(_.isSAMEnabled)

    def literalTypesEnabled: Boolean = isDefinedInModuleOrProject(_.literalTypesEnabled)

    def partialUnificationEnabled: Boolean = isDefinedInModuleOrProject(_.isPartialUnificationEnabled)

    def newCollectionsFramework: Boolean = module.exists(_.hasNewCollectionsFramework)

    def isMetaEnabled: Boolean =
      element.isValid && (element.getContainingFile match {
        case file: ScalaFile if !file.isCompiled => file.isMetaEnabled
        case _ => false
      })

    def defaultImports: Seq[String] = PrecedenceTypes.forElement(element).defaultImports

    private def isDefinedInModuleOrProject(predicate: Module => Boolean): Boolean =
      inThisModuleOrProject(predicate).getOrElse(false)

    private def inThisModuleOrProject[T](predicate: Module => T): Option[T] =
      module
        .orElse(worksheetModule)
        .orElse(element.getProject.anyScalaModule)
        .map(predicate)

    private def worksheetModule: Option[Module] =
      element.getContainingFile.toOption
        .flatMap(_.getUserData(UserDataKeys.SCALA_ATTACHED_MODULE).toOption)
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
