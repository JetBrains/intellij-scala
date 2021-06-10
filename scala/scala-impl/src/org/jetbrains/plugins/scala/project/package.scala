package org.jetbrains.plugins.scala

import com.intellij.ProjectTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.module._
import com.intellij.openapi.project.{DumbService, Project, ProjectUtil}
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{LanguageSubstitutors, PsiElement, PsiFile}
import com.intellij.util.PathsList
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.{ScalaPluginJars, UnloadAwareDisposable}
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.module.SbtModuleType

import java.io.File
import java.net.URL
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.ref.Reference

/**
 * @author Pavel Fatin
 */
package object project {

  object UserDataKeys {

    // used to "attach" a module to some scala file, which is out of any module for some reason
    // the primary purpose is to attach a module for a scala scratch file
    val SCALA_ATTACHED_MODULE = new Key[Reference[Module]]("ScalaAttachedModule")
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

    private val LibraryVersion = "(?<=[:\\-])\\d+\\.\\d+\\.\\d+[^:\\s]*".r

    private[this] val RuntimeLibrary = "((?:scala|dotty|scala3)-library).+".r

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
      module.getName.endsWith(org.jetbrains.sbt.Sbt.BuildModuleSuffix)

    def isSourceModule: Boolean = SbtModuleType.unapply(module).isEmpty

    def hasScala: Boolean =
      scalaModuleSettings.isDefined

    // TODO Generalize: hasScala(Version => Boolean), hasScala(_ >= Scala3)
    def hasScala3: Boolean =
      scalaModuleSettings.exists(_.hasScala3)

    def languageLevel: Option[ScalaLanguageLevel] =
      scalaModuleSettings.map(_.scalaLanguageLevel)

    def hasNewCollectionsFramework: Boolean =
      scalaModuleSettings.exists(_.hasNewCollectionsFramework)

    def isIdBindingEnabled: Boolean =
      scalaModuleSettings.exists(_.isIdBindingEnabled)

    def scalaSdk: Option[LibraryEx] =
      scalaModuleSettings.flatMap(_.scalaSdk)

    def isSharedSourceModule: Boolean = ModuleType.get(module).getId == "SHARED_SOURCES_MODULE"

    def isScalaJs: Boolean =
      scalaModuleSettings.exists(_.isScalaJs)

    def isScalaNative: Boolean =
      scalaModuleSettings.exists(_.isScalaNative)

    def hasNoIndentFlag: Boolean = scalaModuleSettings.exists(_.hasNoIndentFlag)
    def hasOldSyntaxFlag: Boolean = scalaModuleSettings.exists(_.hasOldSyntaxFlag)

    // http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
    // Significant indentation is enabled by default.
    // It can be turned off by giving any of the options -no-indent, -old-syntax and -language:Scala2
    // (NOTE: looks like -language:Scala2 doesn't affect anything in the compiler)
    def isScala3IndentationBasedSyntaxEnabled: Boolean = !(hasNoIndentFlag || hasOldSyntaxFlag)

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

    def dependencyModules: Seq[Module] = {
      val manager = ModuleManager.getInstance(module.getProject)
      manager.getModules.filter(manager.isModuleDependent(module, _)).toSeq
    }

    def withDependencyModules: Seq[Module] =
      module +: dependencyModules

    def modifiableModel: ModifiableRootModel =
      ModuleRootManager.getInstance(module).getModifiableModel

    def libraries: Set[Library] = {
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

    def configureScalaCompilerSettingsFrom(source: String, options: collection.Seq[String]): Unit = {
      val baseDirectory = Option(ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath)
        .getOrElse(module.getProject.getBasePath)
      val compilerSettings = ScalaCompilerSettings.fromOptions(withPathsRelativeTo(baseDirectory, options.toSeq))
      compilerConfiguration.configureSettingsForModule(module, source, compilerSettings)
    }

    private def withPathsRelativeTo(baseDirectory: String, options: Seq[String]): Seq[String] = options.map { option =>
      if (option.startsWith("-Xplugin:")) {
        val path = option.substring(9)
        val absolutePath = if (new File(path).isAbsolute) path else new File(baseDirectory, path).getPath;
        "-Xplugin:" + absolutePath
      } else {
        option
      }
    }

    private def compilerConfiguration =
      ScalaCompilerConfiguration.instanceIn(module.getProject)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] =
      scalaModuleSettings.map(_.scalaLanguageLevel)

    def scalaMinorVersion: Option[ScalaVersion] =
      scalaModuleSettings.flatMap(_.compilerVersion).flatMap(ScalaVersion.fromString)

    def scalaMinorVersionOrDefault: ScalaVersion =
      scalaMinorVersion.getOrElse(ScalaVersion.default)

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

    def kindProjectorUnderscorePlaceholdersEnabled: Boolean =
      scalaModuleSettings.exists(_.kindProjectorUnderscorePlaceholdersEnabled)

    def betterMonadicForPluginEnabled: Boolean =
      scalaModuleSettings.exists(_.betterMonadicForPluginEnabled)

    def contextAppliedPluginEnabled: Boolean =
      scalaModuleSettings.exists(_.contextAppliedPluginEnabled)

    /**
     * Should we check if it's a Single Abstract Method?
     * In 2.11 works with -Xexperimental
     * In 2.12 works by default
     *
     * @return true if language level and flags are correct
     */
    def isSAMEnabled: Boolean =
      scalaModuleSettings.exists(_.isSAMEnabled)

    def isSource3Enabled: Boolean =
      scalaModuleSettings.exists(_.isSource3Enabled)

    def isPartialUnificationEnabled: Boolean =
      scalaModuleSettings.exists(_.isPartialUnificationEnabled)

    def isMetaEnabled: Boolean =
      scalaModuleSettings.exists(_.isMetaEnabled)

    def customDefaultImports: Option[Seq[String]] =
      scalaModuleSettings.flatMap(_.customDefaultImports)
  }

  class ScalaSdkNotConfiguredException(module: Module) extends IllegalArgumentException(s"No Scala SDK configured for module: ${module.getName}")

  implicit class ProjectExt(private val project: Project) extends AnyVal {
    def unloadAwareDisposable: Disposable =
      UnloadAwareDisposable.forProject(project)

    def subscribeToModuleRootChanged(parentDisposable: Disposable = unloadAwareDisposable)
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

    def sourceModules: Seq[Module] = manager.getModules.filter(_.isSourceModule).toSeq

    def modifiableModel: ModifiableModuleModel =
      manager.getModifiableModel

    def hasScala: Boolean = modulesWithScala.nonEmpty

    // TODO Generalize: hasScala(Version => Boolean), hasScala(_ >= Scala3)
    @CachedInUserData(project, ProjectRootManager.getInstance(project))
    def hasScala3: Boolean = modulesWithScala.exists(_.hasScala3)

    def modulesWithScala: Seq[Module] =
      if (project.isDisposed) Seq.empty
      else modulesWithScalaCached

    @CachedInUserData(project, ProjectRootManager.getInstance(project))
    private def modulesWithScalaCached: Seq[Module] =
      modules.filter(_.hasScala)

    def anyScalaModule: Option[Module] =
      modulesWithScala.headOption

    def libraries: Seq[Library] =
      LibraryTablesRegistrar.getInstance.getLibraryTable(project).getLibraries.toSeq

    def baseDir: VirtualFile = ProjectUtil.guessProjectDir(project)

    // TODO: SCL-18097: it should be per-module, like for all other compiler flags (e.g. for isSAMEnabled)
    def isPartialUnificationEnabled: Boolean = modulesWithScala.exists(_.isPartialUnificationEnabled)

    def selectedDocument: Option[Document] =
      Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
        .map(_.getDocument)

    def isIntellijScalaPluginProject: Boolean = {
      val name = project.getName
      name == "scalaUltimate" || name == "scalaCommunity"
    }
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

    def findDocument: Option[Document] =
      Option(FileDocumentManager.getInstance.getDocument(file))

    def toFile: File =
      new File(file.getCanonicalPath)
  }

  implicit class ProjectPsiFileExt(private val file: PsiFile) extends AnyVal {

    def module: Option[Module] = {
      val module1 = attachedFileModule
      val module2 = module1.orElse(projectModule)
      module2
    }

    @CachedInUserData(file, ProjectRootManager.getInstance(file.getProject))
    private def projectModule: Option[Module] =
      inReadAction { // assuming that most of the time it will be read from cache
        val module = ModuleUtilCore.findModuleForPsiElement(file)
        // for build.sbt files the appropriate module is the one with `-build` suffix
        if (module != null && file.is[SbtFileImpl])
          findBuildModule(module)
        else
          Option(module)
      }

    def scratchFileModule: Option[Module] =
      attachedFileModule

    private def attachedFileModule: Option[Module] =
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
      isUnitTestMode && !ProjectPsiFileExt.enableFeaturesCheckInTests ||
        file.module.exists(predicate)
  }

  object ProjectPsiFileExt {
    // TODO: this is a dirty hack to suppress skipping features check in unit tests
    //  ideally we shouldn't check for `isUnitTestMode`, we should fix expected test data in all affected tests
    @TestOnly
    var enableFeaturesCheckInTests = false
  }

  /** @note duplicate in [[org.jetbrains.sbt.annotator.SbtDependencyAnnotator.doAnnotate]] */
  private def findBuildModule(m: Module): Option[Module] = m match {
    case SbtModuleType(_) => Some(m)
    case _ =>                moduleByName(m.getProject, s"${m.getName}${Sbt.BuildModuleSuffix}")
  }

  //noinspection SameParameterValue
  private def moduleByName(project: Project, name: String): Option[Module] =
    ModuleManager.getInstance(project).getModules.find(_.getName == name)

  implicit class ProjectPsiElementExt(private val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(element.getContainingFile).flatMap(_.module)

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    def isInScala3Module: Boolean = module.exists(_.hasScala3)

    def isCompilerStrictMode: Boolean = module.exists(_.isCompilerStrictMode)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] = module.flatMap(_.scalaLanguageLevel)

    def scalaLanguageLevelOrDefault: ScalaLanguageLevel = scalaLanguageLevel.getOrElse(ScalaLanguageLevel.getDefault)

    def scalaMinorVersion: Option[ScalaVersion] = module.flatMap(_.scalaMinorVersion)

    def scalaMinorVersionOrDefault: ScalaVersion = scalaMinorVersion.getOrElse(ScalaVersion.default)

    def kindProjectorPluginEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorPluginEnabled)

    def kindProjectorPlugin: Option[String] = inThisModuleOrProject(_.kindProjectorPlugin).flatten

    def kindProjectorUnderscorePlaceholdersEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorUnderscorePlaceholdersEnabled)

    def betterMonadicForEnabled: Boolean = isDefinedInModuleOrProject(_.betterMonadicForPluginEnabled)

    def contextAppliedEnabled: Boolean = isDefinedInModuleOrProject(_.contextAppliedPluginEnabled)

    def isSAMEnabled: Boolean = isDefinedInModuleOrProject(_.isSAMEnabled)

    def isSource3Enabled: Boolean = isDefinedInModuleOrProject(_.isSource3Enabled)

    def isScala3OrSource3Enabled: Boolean = isDefinedInModuleOrProject(m => m.hasScala3 || m.isSource3Enabled)

    def isScala3IndentationBasedSyntaxEnabled: Boolean = isDefinedInModuleOrProject(_.isScala3IndentationBasedSyntaxEnabled)

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
        case e: IllegalArgumentException => //noinspection ReferencePassedToNls
          throw new ExecutionException(e.getMessage.replace("SDK", "facet"))
      }

    def addRunners(): Unit = list.add(ScalaPluginJars.runnersJar)
  }
}
