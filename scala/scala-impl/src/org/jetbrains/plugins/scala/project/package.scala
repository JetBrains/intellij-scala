package org.jetbrains.plugins.scala

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
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.project.ScalaFeatures.SerializableScalaFeatures
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.{ScalaPluginJars, UnloadAwareDisposable}
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.language.SbtFile
import org.jetbrains.sbt.project.module.SbtModuleType

import java.io.File
import java.net.URL
import scala.annotation.unused
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.ref.Reference

package object project {

  object UserDataKeys {

    /**
     * This key is used to "attach" a module to some scala file, which doesn't belong to any module<br>
     * The primary purpose is to attach a module to scala scratch files<br>
     * Such files are located outside any module scope and behave as Scala Worksheets by default
     */
    val SCALA_ATTACHED_MODULE = new Key[Reference[Module]]("ScalaAttachedModule")
  }

  implicit class LibraryExt(private val library: Library) extends AnyVal {

    import LibraryExt._

    def isScalaSdk: Boolean = library match {
      case libraryEx: LibraryEx => libraryEx.isScalaSdk
      case _ => false
    }

    def libraryVersion: Option[String] = name.flatMap(LibraryVersion.findFirstIn)

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

    private def scalaModuleSettings: Option[ScalaModuleSettings] = cachedInUserData("scalaModuleSettings", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
      ScalaModuleSettings(module)
    }

    /**
     * @return true if module "looks like" a build module (if module name has `-build` suffux)
     */
    def isBuildModule: Boolean =
      module.getName.endsWith(Sbt.BuildModuleSuffix)

    /**
     * @return true if module hast SbtModuleType (work with SBT projects and with BSP projects which use SBT as server)
     * @note we now have two methods: isBuildModule and hasBuildModuleType<br>
     *       isBuildModule is actually something like `looksLikeBuildModule` because it only checks module name<br>
     *       hasBuildModuleType truly checks if the module is reported as build module by SBT<br>
     *       We could deduplicate and leave just one method `isBuildModule`<br>
     *       However it might be not that simple. E.g. in BSP projects module there will be no SbtModuleType
     *       reported for build module (see See SCL-19738)
     *       We need a way to truly check for BSP projects as well
     *
     *       `isBuildModule` is mostly-used because it's simple and cheap (it just checks the name)<br>
     *       And maybe it would be even ok to just leave this simple implementation.
     *       However I decided to leave `isBuildModule2` just because it was already used in some parts
     *       (the method was previously `org.jetbrains.sbt.project.module.SbtModuleType.unapply`)
     *
     */
    def hasBuildModuleType: Boolean = {
      val moduleType = ModuleType.get(module)
      moduleType.isInstanceOf[SbtModuleType]
    }

    def isSourceModule: Boolean = !hasBuildModuleType

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

    /**
     * Selects dependent module for shared-sources module<br>
     * It first search for JVM, then for Js and then for Native
     */
    def findRepresentativeModuleForSharedSourceModule: Option[Module] = cachedInUserData("findRepresentativeModuleForSharedSourceModule", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
      if (isSharedSourceModule) {
        val moduleManager = ModuleManager.getInstance(module.getProject)
        val dependents = moduleManager.getModuleDependentModules(module).asScala
        dependents.find(_.isJvmModule)
          .orElse(dependents.find(_.isScalaJs))
          .orElse(dependents.find(_.isScalaNative))
      }
      else None
    }

    def findRepresentativeModuleForSharedSourceModuleOrSelf: Module =
      findRepresentativeModuleForSharedSourceModule.getOrElse(module)

    def sharedSourceDependency: Option[Module] =
      ModuleRootManager.getInstance(module).getDependencies
        .find(_.isSharedSourceModule)

    /**
     * NOTE: for some projects there are multiple shared-source roots are created.<br>
     * This is done even if some shared-source roots are actually empty (the structure is reported by SBT).
     */
    def sharedSourceDependencies: Seq[Module] =
      ModuleRootManager.getInstance(module).getDependencies
        .filter(_.isSharedSourceModule).toSeq

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

    def configureScalaCompilerSettingsFrom(source: String, options: collection.Seq[String], compileOrder: CompileOrder = CompileOrder.Mixed): Unit = {
      val baseDirectory = Option(ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath)
        .getOrElse(module.getProject.getBasePath)
      val compilerSettings = ScalaCompilerSettings.fromOptions(withPathsRelativeTo(baseDirectory, options.toSeq), compileOrder)
      compilerConfiguration.configureSettingsForModule(module, source, compilerSettings)
    }

    private def withPathsRelativeTo(baseDirectory: String, options: Seq[String]): Seq[String] = options.map { option =>
      if (option.startsWith("-Xplugin:")) {
        val compoundPath = option.substring(9)
        val compoundPathAbsolute = toAbsoluteCompoundPath(baseDirectory, compoundPath)
        "-Xplugin:" + compoundPathAbsolute
      } else {
        option
      }
    }

    // SCL-11861, SCL-18534
    private def toAbsoluteCompoundPath(baseDirectory: String, compoundPath: String): String = {
      // according to https://docs.scala-lang.org/overviews/compiler-options/index.html
      // `,` is used as plugins separator: `-Xplugin PATHS1,PATHS2`
      // but in SCL-11861 `;` is used
      val pluginSeparator = if (compoundPath.contains(";")) ';' else ','

      val paths = compoundPath.split(pluginSeparator)
      val pathsAbsolute = paths.map(toAbsolutePath(baseDirectory, _))
      pathsAbsolute.mkString(pluginSeparator.toString)
    }

    private def toAbsolutePath(baseDirectory: String, path: String): String = {
      val file = new File(path).isAbsolute
      if (file) path
      else new File(baseDirectory, path).getPath
    }

    private def compilerConfiguration =
      ScalaCompilerConfiguration.instanceIn(module.getProject)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] =
      scalaModuleSettings.map(_.scalaLanguageLevel)

    def scalaMinorVersion: Option[ScalaVersion] =
      scalaModuleSettings.flatMap(_.scalaMinorVersion)

    def scalaMinorVersionOrDefault: ScalaVersion =
      scalaMinorVersion.getOrElse(ScalaVersion.default)

    def isCompilerStrictMode: Boolean =
      scalaModuleSettings.exists(_.isCompilerStrictMode)

    def scalaCompilerClasspath: Seq[File] = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.compilerClasspath
      }

    def customScalaCompilerBridgeJar: Option[File] = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.compilerBridgeBinaryJar
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

    def YKindProjectorOptionEnabled: Boolean =
      scalaModuleSettings.exists(_.YKindProjectorOptionEnabled)

    def YKindProjectorUnderscoresOptionEnabled: Boolean =
      scalaModuleSettings.exists(_.YKindProjectorUnderscoresOptionEnabled)

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
      scalaModuleSettings.exists(_.hasSource3Flag)

    def features: SerializableScalaFeatures =
      scalaModuleSettings.fold(ScalaFeatures.default)(_.features)

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
        ModuleRootListener.TOPIC,
        new ModuleRootListener {
          override def rootsChanged(event: ModuleRootEvent): Unit = onRootsChanged(event)
        }
      )

    private def manager =
      ModuleManager.getInstance(project)

    def modules: Seq[Module] =
      manager.getModules.toSeq

    def modifiableModel: ModifiableModuleModel =
      manager.getModifiableModel

    def hasScala: Boolean = modulesWithScala.nonEmpty

    // TODO Generalize: hasScala(Version => Boolean), hasScala(_ >= Scala3)
    def hasScala2: Boolean = cachedInUserData("hasScala2", project, ProjectRootManager.getInstance(project)) {
      modulesWithScala.exists(_.scalaLanguageLevel.exists(_.isScala2))
    }

    def hasScala3: Boolean = cachedInUserData("hasScala3", project, ProjectRootManager.getInstance(project)) {
      modulesWithScala.exists(_.hasScala3)
    }

    def indentationBasedSyntaxEnabled(features: ScalaFeatures): Boolean =
      features.isScala3 &&
        features.indentationBasedSyntaxEnabled &&
        ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX

    /**
     * @return list of modules with Scala SDK setup
     * @note it doesn't return any *-build modules even though it contains syntetic
     */
    def modulesWithScala: Seq[Module] =
      if (project.isDisposed) Seq.empty
      else modulesWithScalaCached

    private def modulesWithScalaCached: Seq[Module] = cachedInUserData("modulesWithScalaCached", project, ProjectRootManager.getInstance(project)) {
      modules.filter(m => m.hasScala && !m.isBuildModule)
    }

    def anyScalaModule: Option[Module] =
      modulesWithScala.headOption

    def libraries: Seq[Library] =
      LibraryTablesRegistrar.getInstance.getLibraryTable(project).getLibraries.toSeq

    def baseDir: VirtualFile = ProjectUtil.guessProjectDir(project)

    // TODO: SCL-18097: it should be per-module, like for all other compiler flags (e.g. for isSAMEnabled)
    def isPartialUnificationEnabled: Boolean = modulesWithScala.exists(_.isPartialUnificationEnabled)

    @deprecated("Use FileEditorManager directly")
    @unused("Can't delete the method right now because can't ensure it's not used externally (Find external usages is broken for extension methods)")
    def selectedDocument: Option[Document] =
      Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
        .map(_.getDocument)

    def isIntellijScalaPluginProject: Boolean = {
      val name = project.getName
      name == "scalaUltimate" || name == "scalaCommunity"
    }

    def allScalaVersions: Seq[ScalaVersion] = {
      val modules = modulesWithScala
      val scalaVersions = modules.flatMap(_.scalaMinorVersion)
      scalaVersions.distinct
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

  // TODO May also be a library file (source or compiled), SCL-20935
  implicit class ProjectPsiFileExt(private val file: PsiFile) extends AnyVal {

    /** TODO: document, maybe even rename to something better, like "actual module", "effective module" */
    def module: Option[Module] = attachedFileModule.orElse {
      cachedInUserData("module", file, ProjectRootManager.getInstance(file.getProject)) {
        inReadAction { // assuming that most of the time it will be read from cache
          val module = {
            val virtualFile = if (file.getVirtualFile != null) file.getVirtualFile else file.getOriginalFile.getVirtualFile
            val isFileInLibrary = virtualFile != null && ProjectFileIndex.getInstance(file.getProject).isInLibrary(virtualFile)
            if (isFileInLibrary)
              null
            else
              ModuleUtilCore.findModuleForPsiElement(file)
          }
          // for build.sbt files the appropriate module is the one with `-build` suffix
          //noinspection ApiStatus
          if (module != null) {
            file match {
              case sbtFile: SbtFile =>
                sbtFile.findBuildModule(module)
              case _ =>
                Option(module)
            }
          } else
            Option(module)
        }
      }
    }

    def scratchFileModule: Option[Module] =
      attachedFileModule

    private def attachedFileModule: Option[Module] =
      Option(file.getUserData(UserDataKeys.SCALA_ATTACHED_MODULE)).flatMap(_.get)

    def isMetaEnabled: Boolean =
      !ScStubElementType.Processing.isRunning &&
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

  // TODO The same as ScalaFeatures (Scala versions, isSource3Enabled vs hasSource3Flag, etc.), SCL-20935
  implicit class ProjectPsiElementExt(private val element: PsiElement) extends AnyVal {
    def module: Option[Module] = Option(element.getContainingFile).flatMap(_.module)

    def isInScalaModule: Boolean = module.exists(_.hasScala)

    // TODO Used as isInScala3File, but library files have no module, SCL-20935
    // TODO Library source files are not compiled, SCL-20935
    def isInScala3Module: Boolean =
      Option(element.getContainingFile).exists(file => file.getName.endsWith(".tasty")) ||
        module.exists(_.hasScala3)

    def isCompilerStrictMode: Boolean = module.exists(_.isCompilerStrictMode)

    def scalaLanguageLevel: Option[ScalaLanguageLevel] =
      fromFeaturesOrModule(_.languageLevel, _.scalaLanguageLevel)

    private def fromFeaturesOrModule[T](getFromFeatures: ScalaFeatures => T, getFromModule: Module => Option[T]): Option[T] = {
      val fromFeatures = featuresOpt.map(getFromFeatures)
      val orFromModule = fromFeatures.orElse(module.flatMap(getFromModule))
      orFromModule
    }

    def scalaLanguageLevelOrDefault: ScalaLanguageLevel = scalaLanguageLevel.getOrElse(ScalaLanguageLevel.getDefault)

    def scalaMinorVersion: Option[ScalaVersion] = module.flatMap(_.scalaMinorVersion)

    def scalaMinorVersionOrDefault: ScalaVersion = scalaMinorVersion.getOrElse(ScalaVersion.default)

    /**
     * Is kind-projector plugin enabled or is -Ykind-projector scala 3 compiler option set.
     */
    def kindProjectorEnabled: Boolean =
      kindProjectorPluginEnabled || YKindProjectorOptionEnabled || YKindProjectorUnderscoresOptionEnabled

    def underscoreWidlcardsDisabled: Boolean =
      kindProjectorUnderscorePlaceholdersEnabled || YKindProjectorUnderscoresOptionEnabled

    def kindProjectorPluginEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorPluginEnabled)

    def kindProjectorPlugin: Option[String] = inThisModuleOrProject(_.kindProjectorPlugin).flatten

    def kindProjectorUnderscorePlaceholdersEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorUnderscorePlaceholdersEnabled)

    def YKindProjectorOptionEnabled: Boolean = isDefinedInModuleOrProject(_.YKindProjectorOptionEnabled)

    def YKindProjectorUnderscoresOptionEnabled: Boolean = isDefinedInModuleOrProject(_.YKindProjectorUnderscoresOptionEnabled)

    def betterMonadicForEnabled: Boolean = isDefinedInModuleOrProject(_.betterMonadicForPluginEnabled)

    def contextAppliedEnabled: Boolean = isDefinedInModuleOrProject(_.contextAppliedPluginEnabled)

    def isSAMEnabled: Boolean = isDefinedInModuleOrProject(_.isSAMEnabled)

    def isSource3Enabled: Boolean = isDefinedInModuleOrProject(_.isSource3Enabled)

    def isScala3OrSource3Enabled: Boolean = isDefinedInModuleOrProject(m => m.hasScala3 || m.isSource3Enabled)

    private def featuresOpt: Option[SerializableScalaFeatures] = {
      val file = Option(element.getContainingFile)
      val featuresFromFile = file.flatMap(ScalaFeatures.getAttachedScalaFeatures)

      val orFeaturesFromModule = (featuresFromFile match {
          case Some(s: SerializableScalaFeatures) => Some(s)
          case _ => None
        })
        .orElse(inThisModuleOrProject(_.features))

      orFeaturesFromModule
    }

    def features: SerializableScalaFeatures =
      featuresOpt.getOrElse(ScalaFeatures.default)

    def literalTypesEnabled: Boolean = {
      val file = element.getContainingFile
      file != null && (file.getLanguage == Scala3Language.INSTANCE || file.isDefinedInModuleOrProject(_.literalTypesEnabled))
    }

    def partialUnificationEnabled: Boolean = isDefinedInModuleOrProject(_.isPartialUnificationEnabled)

    // TODO Determine Scala version of libraries without using module, SCL-20935
    def newCollectionsFramework: Boolean = module.exists(_.hasNewCollectionsFramework)

    def isMetaEnabled: Boolean =
      element.isValid && (element.getContainingFile match {
        case file: ScalaFile if !file.isCompiled => file.isMetaEnabled
        case _ => false
      })

    def defaultImports: Seq[String] = PrecedenceTypes.forElement(element).defaultImports

    private[ProjectPsiElementExt] def isDefinedInModuleOrProject(predicate: Module => Boolean): Boolean =
      inThisModuleOrProject(predicate).getOrElse(false)

    // TODO Predicates are not applicable to library files, because they have neither module nor project, SCL-20935
    // TODO Library source files are not compiled, SCL-20935
    private def inThisModuleOrProject[T](predicate: Module => T): Option[T] =
      if (element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)) None
      else module.orElse(element.getProject.anyScalaModule).map(predicate)
  }

  implicit class PathsListExt(private val list: PathsList) extends AnyVal {

    def addScalaCompilerClassPath(module: Module): Unit =
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
