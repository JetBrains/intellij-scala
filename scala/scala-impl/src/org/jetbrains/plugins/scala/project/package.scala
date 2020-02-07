package org.jetbrains.plugins.scala

import java.io.File
import java.net.URL

import com.intellij.ProjectTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module._
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{LanguageSubstitutors, PsiElement, PsiFile}
import com.intellij.util.PathsList
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.module.SbtModuleType

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.ref.WeakReference

/**
 * @author Pavel Fatin
 */
package object project {

  object UserDataKeys {

    // used to "attach" a module to some scala file, which is out of any module for some reason
    // the primary purpose is to attach a module for a scala scratch file
    val SCALA_ATTACHED_MODULE = new Key[WeakReference[Module]]("ScalaAttachedModule")
  }

  implicit class LibraryExt(private val library: Library) extends AnyVal {

    import LibraryExt._

    def isScalaSdk: Boolean = library match {
      case libraryEx: LibraryEx => libraryEx.isScalaSdk
      case _ => false
    }

    def compilerVersion: Option[String] = name.flatMap(LibraryVersion.findFirstIn)

    def hasRuntimeLibrary: Boolean = name.exists(isRuntimeLibrary)

    private def name: Option[String] = Option(library.getName)

    def jarUrls: Set[URL] =
      library
        .getFiles(OrderRootType.CLASSES)
        .map(_.getPath)
        .map(path => new URL(s"jar:file://$path"))
        .toSet
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

    @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
    private def scalaModuleSettings: Option[ScalaModuleSettings] =
      ScalaModuleSettings(module)

    def isBuildModule: Boolean =
      module.getName.endsWith("-build")

    def isSourceModule: Boolean = SbtModuleType.unapply(module).isEmpty

    def hasScala: Boolean =
      scalaModuleSettings.isDefined

    def hasScala3: Boolean =
      scalaModuleSettings.exists(_.hasScala3)

    def hasNewCollectionsFramework: Boolean =
      scalaModuleSettings.exists(_.hasNewCollectionsFramework)

    def isIdBindingEnabled: Boolean =
      scalaModuleSettings.exists(_.isIdBindingEnabled)

    def scalaSdk: Option[LibraryEx] =
      scalaModuleSettings.map(_.scalaSdk)

    def isSharedSourceModule: Boolean = ModuleType.get(module).getId == "SHARED_SOURCES_MODULE"

    def isScalaJs: Boolean =
      scalaModuleSettings.exists(_.isScalaJs)

    def isScalaNative: Boolean =
      scalaModuleSettings.exists(_.isScalaNative)

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

    def libraries: collection.Set[Library] = {
      val processor = new CollectUniquesProcessorEx[Library]()
      OrderEnumerator.orderEntries(module)
        .librariesOnly()
        .forEachLibrary(processor)

      processor.results
    }

    def sbtVersion: Option[Version] =
      scalaModuleSettings.flatMap(_.sbtVersion)

    def isTrailingCommasEnabled: Boolean =
      scalaModuleSettings.exists(_.isTrailingCommasEnabled)

    def scalaCompilerSettingsProfile: ScalaCompilerSettingsProfile =
      compilerConfiguration.getProfileForModule(module)

    def scalaCompilerSettings: ScalaCompilerSettings =
      compilerConfiguration.getSettingsForModule(module)

    def configureScalaCompilerSettingsFrom(source: String, options: Seq[String]): Unit =
      compilerConfiguration.configureSettingsForModule(module, source, ScalaCompilerSettings.fromOptions(options))

    def scalaLanguageLevel: Option[ScalaLanguageLevel] =
      scalaModuleSettings.map(_.scalaLanguageLevel)

    def isCompilerStrictMode: Boolean =
      scalaModuleSettings.exists(_.isCompilerStrictMode)

    def scalaCompilerClasspath: Seq[File] = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.compilerClasspath
      }

    def literalTypesEnabled: Boolean =
      scalaModuleSettings.exists(_.literalTypesEnabled)

    /**
     * @see https://github.com/non/kind-projector
     */
    def kindProjectorPluginEnabled: Boolean =
      kindProjectorPlugin.isDefined

    def kindProjectorPlugin: Option[String] =
      scalaModuleSettings.flatMap(_.kindProjectorPlugin)

    def betterMonadicForPluginEnabled: Boolean =
      scalaModuleSettings.exists(_.betterMonadicForPluginEnabled)

    /**
     * Should we check if it's a Single Abstract Method?
     * In 2.11 works with -Xexperimental
     * In 2.12 works by default
     *
     * @return true if language level and flags are correct
     */
    def isSAMEnabled: Boolean =
      scalaModuleSettings.exists(_.isSAMEnabled)

    def isPartialUnificationEnabled: Boolean =
      scalaModuleSettings.exists(_.isPartialUnificationEnabled)

    def isMetaEnabled: Boolean =
      scalaModuleSettings.exists(_.isMetaEnabled)

    def customDefaultImports: Option[Seq[String]] =
      scalaModuleSettings.flatMap(_.customDefaultImports)

    private def compilerConfiguration =
      ScalaCompilerConfiguration.instanceIn(module.getProject)
  }

  class ScalaSdkNotConfiguredException(module: Module) extends IllegalArgumentException(s"No Scala SDK configured for module: ${module.getName}")

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
      LibraryTablesRegistrar.getInstance.getLibraryTable(project).getLibraries.toSeq

    def baseDir: VirtualFile = LocalFileSystem.getInstance().findFileByPath(project.getBasePath)

    def isPartialUnificationEnabled: Boolean = modulesWithScala.exists(_.isPartialUnificationEnabled)

    def selectedDocument: Option[Document] =
      Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
        .map(_.getDocument)
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

    def module: Option[Module] = projectModule.orElse(file.scratchFileModule)

    @CachedInUserData(file, ProjectRootManager.getInstance(file.getProject))
    private def projectModule: Option[Module] =
      Option(ModuleUtilCore.findModuleForPsiElement(file))

    def scratchFileModule: Option[Module] =
      Option(file.getUserData(UserDataKeys.SCALA_ATTACHED_MODULE)).flatMap(_.get)

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
      isUnitTestMode ||
        file.module.exists(predicate)
  }

  implicit class ProjectPsiElementExt(private val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(element.getContainingFile).flatMap(_.module)

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    def isInScala3Module: Boolean = module.exists(_.hasScala3)

    def isCompilerStrictMode: Boolean = module.exists(_.isCompilerStrictMode)

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
        .orElse(element.getProject.anyScalaModule)
        .map(predicate)
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
