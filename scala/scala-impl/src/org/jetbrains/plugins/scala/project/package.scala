package org.jetbrains.plugins.scala

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.module._
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project, ProjectUtil}
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.workspace.jps.entities.{DependencyScope, _}
import com.intellij.platform.workspace.storage.{EntitySource, MutableEntityStorage}
import com.intellij.psi.{LanguageSubstitutors, PsiElement, PsiFile}
import com.intellij.util.PathsList
import org.jetbrains.annotations.{ApiStatus, TestOnly}
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.project.LibraryExt.{guessLibraryVersionFromName, runtimeVersion}
import org.jetbrains.plugins.scala.project.ScalaFeatures.SerializableScalaFeatures
import org.jetbrains.plugins.scala.project.external.CompanionProxyUtils
import org.jetbrains.plugins.scala.project.external.CompanionProxyUtils.LegacyBridgeModifiableBaseCompanion
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.tasty.reader.CompilerOptions
import org.jetbrains.plugins.scala.util.{ScalaPluginJars, UnloadAwareDisposable}
import org.jetbrains.sbt.language.SbtFile
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.{Sbt, WorkspaceModelUtil}

import java.net.{URI, URL}
import java.nio.file.Path
import kotlin.Unit.{INSTANCE => KUnit}
import scala.annotation.unused
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.ref.Reference
import scala.util.Try

package object project {

  object UserDataKeys {

    /**
     * This key is used to "attach" a module to some scala file, which doesn't belong to any module<br>
     * The primary purpose is to attach a module to scala scratch files<br>
     * Such files are located outside any module scope and behave as Scala Worksheets by default
     */
    val SCALA_ATTACHED_MODULE = new Key[Reference[Module]]("ScalaAttachedModule")

    //TODO: make sure that any modification to module increments the moddule mod counter
  }

  implicit class LibraryExt(private val library: Library) extends AnyVal with LibraryBase {

    import LibraryExt._

    override def isScalaSdk: Boolean = library match {
      case libraryEx: LibraryEx => libraryEx.isScalaSdk
      case _ => false
    }

    override def name: Option[String] = Option(library.getName)

    def hasRuntimeLibrary: Boolean = name.exists(isRuntimeLibrary)

    def jarUrls: Set[URL] =
      library
        .getFiles(OrderRootType.CLASSES)
        .map(_.getPath)
        .map(path => new URI(s"jar:file://$path").toURL)
        .toSet
  }

  object LibraryExt {

    @TestOnly
    def guessLibraryVersionFromName(libraryName: String): Option[String] =
      LibraryVersion.findFirstIn(libraryName)

    /**
     * Examples (see tests for more examples):
     *  - anything-here-1.22.3
     *  - anything-here:1.22.3
     *  - anything-here_1.22.3
     *  - anything-here-1.22.3-bin-db-2-fd41f6b
     */
    private val LibraryVersion = """(?<=[:_\-])\d+\.\d+\.\d+[^:\s]*""".r

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

  implicit class MutableEntityStorageExt(private val storage: MutableEntityStorage) extends AnyVal {
    /**
     * Adds a project-level [[LibraryEntity]] using a JPS-backed entity source.
     * Use this only for build tools whose entities can be serialized with JPS.
     */
    def addLibraryEntity(libraryName: String, project: Project, sourceId: String, roots: Seq[LibraryRoot] = Seq.empty): LibraryEntity =
      addLibraryEntity(
        libraryName,
        roots,
        entitySource = {
          val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(sourceId)
          val legacyBridgeModifiableBase = CompanionProxyUtils.LegacyBridgeJpsEntitySourceFactoryCompanion.getInstance(project)
          legacyBridgeModifiableBase.createEntitySourceForProjectLibrary(externalSource)
        }
      )

    def addLibraryEntity(libraryName: String, roots: Seq[LibraryRoot], entitySource: => EntitySource): LibraryEntity = {
      val libraryId = new LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId.INSTANCE)
      val existingLibrary = storage.resolve(libraryId)
      if (existingLibrary != null) existingLibrary
      else {
        val libraryEntity = LibraryEntityModifications.createLibraryEntity(libraryName, LibraryTableId.ProjectLibraryTableId.INSTANCE, roots.asJava, entitySource)
        storage.addEntity[LibraryEntityBuilder, LibraryEntity](libraryEntity)
      }
    }
  }

  implicit class LibraryEntityExt(private val libraryEntity: LibraryEntity) extends AnyVal with LibraryBase {
    override def isScalaSdk: Boolean = {
      val typeId = libraryEntity.getTypeId
      typeId != null && typeId.getName == ScalaLibraryType.Kind.getKindId
    }

    override def name: Option[String] = Option(libraryEntity.getName)

    /**
     * Written based on [[com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryModifiableModelBridgeImpl#setProperties(com.intellij.openapi.roots.libraries.LibraryProperties)]] and
     * [[com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryModifiableModelBridgeImpl#updateProperties(java.lang.String, java.lang.String)]].
     * At the moment, there is no equivalent of these methods in the Workspace model.
     * Remove it when it's implemented on the platform side.
     */
    def setScalaProperties(
      scalaLibraryProperties: ScalaLibraryProperties,
      storage: MutableEntityStorage
    ): Unit = {
      val kind = libraryEntity.getTypeId
      if (kind == null) return

      val scalaLibraryPropertiesXmlTag = LegacyBridgeModifiableBaseCompanion.serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, scalaLibraryProperties)
      val libraryProperties = Library_extensionsKt.getLibraryProperties(libraryEntity)

      if (libraryProperties == null) {
        LibraryEntityModifications.modifyLibraryEntity(storage, libraryEntity, builder => {
          val entity = LibraryPropertiesEntityModifications.createLibraryPropertiesEntity(libraryEntity.getEntitySource)
          entity.setPropertiesXmlTag(scalaLibraryPropertiesXmlTag)
          LibraryEntityModifications.setLibraryProperties(builder, entity)
          KUnit
        })
      } else {
        LibraryPropertiesEntityModifications.modifyLibraryPropertiesEntity(storage, libraryProperties, builder => {
          builder.setPropertiesXmlTag(scalaLibraryPropertiesXmlTag)
          KUnit
        })
      }
    }

    def setScalaKind(storage: MutableEntityStorage): Unit =
      storage.modifyEntity[LibraryEntityBuilder, LibraryEntity](classOf[LibraryEntityBuilder], libraryEntity, { builder =>
        val libraryTypeId = new LibraryTypeId(ScalaLibraryType.Kind.getKindId)
        builder.setTypeId(libraryTypeId)
        KUnit
      })
  }

  trait LibraryBase extends Any {
    def isScalaSdk: Boolean
    def name: Option[String]
    def libraryVersion: Option[String] = name.flatMap(guessLibraryVersionFromName)
    def jarLibraryVersion: Option[String] = name.flatMap(runtimeVersion)
  }

  implicit class ModuleExt(private val module: Module) extends AnyVal {

    // Since 263.4312 the platform throws AlreadyDisposedException when the roots of a disposed
    // module are queried (ModuleBridgeUtils.findModuleEntitityLegacy), instead of returning its
    // pinned stale entity, so the OrderEnumerator query in ScalaModuleSettings would fail here.
    // Bailing out early also avoids the project-service lookups that `cachedInUserData` performs.
    private def scalaModuleSettings: Option[ScalaModuleSettings] =
      if (module.isDisposed) None
      else cachedInUserData("scalaModuleSettings", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
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
    def hasScala2: Boolean =
      scalaModuleSettings.exists(_.scalaLanguageLevel.isScala2)

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
      } else if (isSharedSourceModule) {
        val sharedSourcesOwnerModules = getSharedSourcesModulesOwners(module)
        sharedSourcesOwnerModules.find(_.isJvmModule)
      } else {
        sharedSourceDependency.flatMap(_.findJVMModule)
      }
    }

    /**
     * Selects dependent module for shared-sources module<br>
     * It first search for JVM, then for Js and then for Native
     */
    def findRepresentativeModuleForSharedSourceModule: Option[Module] = cachedInUserData("findRepresentativeModuleForSharedSourceModule", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
      if (isSharedSourceModule) {
        val sharedSourcesOwnerModules = getSharedSourcesModulesOwners(module)
        sharedSourcesOwnerModules.find(_.isJvmModule)
          .orElse(sharedSourcesOwnerModules.find(_.isScalaJs))
          .orElse(sharedSourcesOwnerModules.find(_.isScalaNative))
      }
      else None
    }

    private def getSharedSourcesModulesOwners(module: Module): Seq[Module] = {
      val moduleManager = ModuleManager.getInstance(module.getProject)
      val sharedSourcesOwnersEntity = WorkspaceModelUtil.getSharedSourcesOwnersEntity(module)

      sharedSourcesOwnersEntity match {
        case Some(entity) =>
          val ownerModuleIds = entity.getOwnerModuleIds.asScala
          val modules = moduleManager.getModifiableModel.getModules.toSeq
          modules.filter { module =>
            val projectId = ExternalSystemApiUtil.getExternalProjectId(module)
            ownerModuleIds.contains(projectId)
          }
        case _ => Seq.empty
      }
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

    def configureScalaCompilerSettingsFrom(
      source: String,
      options: collection.Seq[String],
      project: Project,
      compileOrder: CompileOrder = CompileOrder.Mixed
    ): Unit = {
      val baseDirectory = Option(ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath)
        .getOrElse(module.getProject.getBasePath)
      val compilerSettings = ScalaCompilerSettings.fromOptions(withPathsRelativeTo(baseDirectory, options.toSeq, project), compileOrder)
      compilerConfiguration.configureSettingsForModule(module, source, compilerSettings)
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

    def scalaCompilerClasspath: Seq[Path] = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.compilerClasspath
      }

    def customScalaCompilerBridgeJar: Option[Path] = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.compilerBridgeBinaryJar
      }

    def replClasspath: ReplClasspath = module.scalaSdk
      .fold(throw new ScalaSdkNotConfiguredException(module)) {
        _.properties.replClasspath
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

    def XKindProjectorOptionEnabled: Boolean =
      scalaModuleSettings.exists(_.XKindProjectorOptionEnabled)

    def YKindProjectorUnderscoresOptionEnabled: Boolean =
      scalaModuleSettings.exists(_.YKindProjectorUnderscoresOptionEnabled)

    def XKindProjectorUnderscoresOptionEnabled: Boolean =
      scalaModuleSettings.exists(_.XKindProjectorUnderscoresOptionEnabled)

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

    def source3Options: Source3Options = scalaModuleSettings.fold(Source3Options.none)(_.source3Options)
    def isSource3Enabled: Boolean      = source3Options.isSource3Enabled

    def isSource3MigrationEnabled: Boolean = scalaModuleSettings.exists(_.hasSource3Migration)

    def isStrictEqualityFlagEnabled: Boolean = scalaModuleSettings.exists(_.hasStrictEquality)
    def isNamedTypeArgumentsFlagEnabled: Boolean = scalaModuleSettings.exists(_.hasNamedTypeArguments)

    def features: SerializableScalaFeatures =
      scalaModuleSettings.fold(ScalaFeatures.default)(_.features)

    /**
     * Similar as [[features]] but when we don't expect a fallback to the default features.
     * It's designed for tests primarily as in production we expect a fail-safe solution without exceptions.
     *
     * We could consider logging an error in tests universally, everywhere where [[scalaModuleSettings]] returns None in tests.
     * This might identify a lot of tests with a potentially broken setup.
     */
    @TestOnly
    def featuresNonDefault: SerializableScalaFeatures = scalaModuleSettings match {
      case Some(settings) => settings.features
      case None =>
        throw new AssertionError(s"Module ${module.getName} has no ScalaModuleSettings, which is unexpected at this moment")
    }

    def isPartialUnificationEnabled: Boolean =
      scalaModuleSettings.exists(_.isPartialUnificationEnabled)

    def isMetaEnabled: Boolean =
      scalaModuleSettings.exists(_.isMetaEnabled)

    def customDefaultImports: Option[Seq[String]] =
      scalaModuleSettings.flatMap(_.customDefaultImports)

    def externalSystemId: Option[String] =
      scalaModuleSettings.flatMap(_.externalSystemId)
  }

  implicit class ModuleEntityExt(private val moduleEntity: ModuleEntity) extends AnyVal {
    def configureScalaCompilerSettingsFrom(
      project: Project,
      source: String,
      options: collection.Seq[String],
      compileOrder: CompileOrder = CompileOrder.Mixed
    ): Unit = {
      val moduleOptions = ModuleExtensions.getExModuleOptions(moduleEntity)
      val baseDirectory =
        if (moduleOptions != null && moduleOptions.getRootProjectPath != null) {
          moduleOptions.getRootProjectPath
        } else {
          project.getBasePath
        }
      val compilerSettings = ScalaCompilerSettings.fromOptions(withPathsRelativeTo(baseDirectory, options.toSeq, project), compileOrder)
      val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
      compilerConfiguration.configureSettingsForModule(moduleEntity.getName, source, compilerSettings)
    }

    def addLibraryDependency(storage: MutableEntityStorage, libraryEntity: LibraryEntity): Unit =
      ModuleEntityModifications.modifyModuleEntity(storage, moduleEntity, builder => {
        val libraryId = new LibraryId(libraryEntity.getName, libraryEntity.getTableId)
        val libraryDependency = new LibraryDependency(libraryId, false, DependencyScope.COMPILE)
        builder.getDependencies.add(libraryDependency)
        KUnit
      })
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

    /**
     * @note This utility method can end up being called on the UI thread. In the worst-case scenario with changes to
     *       the project structure and dropped caches, this can end up being a very expensive method, especially in
     *       large projects with many modules. Therefore, checking for cancellation often can be very helpful.
     */
    // TODO Generalize: hasScala(Version => Boolean), hasScala(_ >= Scala3)
    def hasScala2: Boolean = cachedInUserData("hasScala2", project, ProjectRootManager.getInstance(project)) {
      modulesWithScala.exists { m =>
        ProgressManager.checkCanceled()
        m.scalaLanguageLevel.exists(_.isScala2)
      }
    }

    /**
     * @note This utility method can end up being called on the UI thread. In the worst-case scenario with changes to
     *       the project structure and dropped caches, this can end up being a very expensive method, especially in
     *       large projects with many modules. Therefore, checking for cancellation often can be very helpful.
     */
    def hasScala3: Boolean = cachedInUserData("hasScala3", project, ProjectRootManager.getInstance(project)) {
      modulesWithScala.exists { m =>
        ProgressManager.checkCanceled()
        m.hasScala3
      }
    }

    //TODO: currently this is an extension method on a project
    // However, it should be an extension method on a file.
    // This is because code style can be per-file (due to .editorconfig files)
    // Also there is SCL-22380 which can alter the code-style behavior per-module
    //
    //TODO: in platform print a warning when code style is used on the in-memory, synthetic file OR alternative conditions - when it's outside source roots with editor config
    // users should explicitly use code style of file.getProject or ensure that the file is physical or something like that
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

    /**
     * @note This utility method can end up being called on the UI thread. In the worst-case scenario with changes to
     *       the project structure and dropped caches, this can end up being a very expensive method, especially in
     *       large projects with many modules. Therefore, checking for cancellation often can be very helpful.
     */
    private def modulesWithScalaCached: Seq[Module] = cachedInUserData("modulesWithScalaCached", project, ProjectRootManager.getInstance(project)) {
      modules.filter { m =>
        ProgressManager.checkCanceled()
        m.hasScala && !m.isBuildModule
      }
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

    def toPath: Path =
      Path.of(file.getCanonicalPath)
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
              null // We do not search for the containing module of library sources in this method implementation.
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
                Some(module)
            }
          } else None
        }
      }
    }

    def scratchFileModule: Option[Module] =
      attachedFileModule

    private def attachedFileModule: Option[Module] =
      Option(file.getVirtualFile).flatMap(vf => Option(vf.getUserData(UserDataKeys.SCALA_ATTACHED_MODULE)).flatMap(_.get))

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
     * Is kind-projector plugin enabled or
     *   is -Ykind-projector scala 3 compiler option set or
     *   is -Xkind-projector scala 3 compiler option set.
     */
    def kindProjectorEnabled: Boolean =
      kindProjectorPluginEnabled ||
        YKindProjectorOptionEnabled ||
        YKindProjectorUnderscoresOptionEnabled ||
        XKindProjectorOptionEnabled ||
        XKindProjectorUnderscoresOptionEnabled

    def underscoreWildcardsDisabled: Boolean =
      kindProjectorUnderscorePlaceholdersEnabled || YKindProjectorUnderscoresOptionEnabled

    def kindProjectorPluginEnabled: Boolean = isDefinedInModuleOrProject(_.kindProjectorPluginEnabled)

    def kindProjectorPlugin: Option[String] = inThisModuleOrProject(_.kindProjectorPlugin).flatten

    def kindProjectorUnderscorePlaceholdersEnabled: Boolean =
      isDefinedInModuleOrProject(_.kindProjectorUnderscorePlaceholdersEnabled)

    def YKindProjectorOptionEnabled: Boolean =
      compilerOptionsFor(element).exists(_.kindProjector) ||
        isDefinedInModuleOrProject(_.YKindProjectorOptionEnabled)

    /** https://youtrack.jetbrains.com/issue/SCL-24252 */
    def XKindProjectorOptionEnabled: Boolean =
      isDefinedInModuleOrProject(_.XKindProjectorOptionEnabled)

    private def compilerOptionsFor(element: PsiElement): Option[CompilerOptions] = containingFileOf(element) match {
      case Some(file: ScClsFileImpl) =>
        file.compilerOptions
      case Some(file: ScalaFile) if file.getVirtualFile != null =>
        if (ProjectFileIndex.getInstance(file.getProject).isInLibrarySource(file.getVirtualFile)) {
          val containingClass = element.contexts.findByType[ScTypeDefinition]
          val fqn = containingClass.map(_.qualifiedName)
          val compiledClass = fqn.flatMap(name => ScalaPsiManager.instance(element.getProject).getCachedClass(element.getResolveScope, name))
          val options = compiledClass.flatMap(compilerOptionsFor)
          options
        } else {
          None
        }
      case _ => None
    }

    private def containingFileOf(element: PsiElement): Option[PsiFile] =
      element.containingFile.map(file => Option(file.getContext).flatMap(containingFileOf).getOrElse(file))

    def YKindProjectorUnderscoresOptionEnabled: Boolean =
      isDefinedInModuleOrProject(_.YKindProjectorUnderscoresOptionEnabled)

    def XKindProjectorUnderscoresOptionEnabled: Boolean =
      isDefinedInModuleOrProject(_.XKindProjectorUnderscoresOptionEnabled)

    def betterMonadicForEnabled: Boolean = isDefinedInModuleOrProject(_.betterMonadicForPluginEnabled)

    def contextAppliedEnabled: Boolean = isDefinedInModuleOrProject(_.contextAppliedPluginEnabled)

    def isSAMEnabled: Boolean = isDefinedInModuleOrProject(_.isSAMEnabled)

    def isStrictEqualityFlagEnabled: Boolean = isDefinedInModuleOrProject(_.isStrictEqualityFlagEnabled)
    def isNamedTypeArgumentsFlagEnabled: Boolean = isDefinedInModuleOrProject(_.isNamedTypeArgumentsFlagEnabled)

    def isStrictEqualityEnabled: Boolean = isStrictEqualityFlagEnabled || {
      val reference =
        ScalaPsiElementFactory.createReferenceFromText("strictEquality", element, element)

      reference.resolve() match {
        case obj: ScObject =>
          val fqn = obj.qualifiedName
          fqn == "scala.language.strictEquality" ||
            fqn == "scala.runtime.stdLibPatches.language.strictEquality"
        case _ => false
      }
    }

    def isNamedTypeArgumentsFeatureImported: Boolean = isNamedTypeArgumentsFlagEnabled || {
      val reference =
        ScalaPsiElementFactory.createReferenceFromText("namedTypeArguments", element, element)

      reference.resolve() match {
        case obj: ScObject =>
          val fqn = obj.qualifiedName
          fqn == "scala.language.experimental.namedTypeArguments" ||
            fqn == "scala.runtime.stdLibPatches.language.experimental.namedTypeArguments"
        case _ => false
      }
    }

    def isSource3MigrationEnabled: Boolean = isDefinedInModuleOrProject(_.isSource3MigrationEnabled)

    def source3Options: Source3Options = module.fold(Source3Options.none)(_.source3Options)
    def isSource3Enabled: Boolean = isDefinedInModuleOrProject(_.isSource3Enabled)
    def noUnicodeEscapesInRawStrings: Boolean = features.noUnicodeEscapesInRawStrings

    def isScala3OrSource3Enabled: Boolean = isDefinedInModuleOrProject(m => m.hasScala3 || m.isSource3Enabled)

    private def featuresOpt: Option[SerializableScalaFeatures] = {
      val file = Option(element.getContainingFile)
      val featuresFromFile = file.flatMap(ScalaFeatures.getAttachedScalaFeatures)
      val featuresFromFileSerializable = featuresFromFile match {
        case Some(s: SerializableScalaFeatures) => Some(s)
        case _ => None
      }

      val featuresFromFileOrModule = featuresFromFileSerializable
        .orElse(inThisModuleOrProject(_.features))

      // As a final fallback, if there are no attached features and no Scala module, use the file language.
      // It can be used in some lightweight tests that don't create any Scala SDK and just specify the language,
      // for example, MultiLineStringEnterHandlerTestBase
      val featuresFromFileOrModuleOrFileLanguage = featuresFromFileOrModule.orElse {
        val fileLanguage = file.map(_.getLanguage)
        fileLanguage.map(ScalaFeatures.defaultForLanguage)
      }

      featuresFromFileOrModuleOrFileLanguage
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
    private def inThisModuleOrProject[T](predicate: Module => T): Option[T] = {
      // Handle classes from libraries
      val isDecompiledScalaFile = element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)
      if (isDecompiledScalaFile)
        None
      else
        module.orElse(element.getProject.anyScalaModule).map(predicate)
    }
  }

  implicit class PathsListExt(private val list: PathsList) extends AnyVal {

    @deprecated("Not eel-aware. Use eel-aware path translation instead. No direct replacement.", "2026.1")
    @Deprecated(forRemoval = true, since = "2026.1")
    @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
    def addScalaCompilerClassPath(module: Module): Unit =
      try {
        val files = module.scalaCompilerClasspath.asJava
        list.addAllPaths(files)
      } catch {
        case e: IllegalArgumentException => //noinspection ReferencePassedToNls
          throw new ExecutionException(e.getMessage.replace("SDK", "facet"))
      }

    def addRunners(): Unit = list.add(ScalaPluginJars.runnersJar)
  }

  private def withPathsRelativeTo(baseDirectory: String, options: Seq[String], project: Project): Seq[String] = {
    val eelDescriptor = EelProviderUtil.getEelDescriptor(project)
    options.map { option =>
      if (option.startsWith("-Xplugin:")) {
        val compoundPath = option.substring(9)
        val compoundPathAbsolute = toAbsoluteCompoundPath(baseDirectory, compoundPath, eelDescriptor)
        "-Xplugin:" + compoundPathAbsolute
      } else {
        option
      }
    }
  }

  // SCL-11861, SCL-18534
  private def toAbsoluteCompoundPath(baseDirectory: String, compoundPath: String, eelDescriptor: EelDescriptor): String = {
    // according to https://docs.scala-lang.org/overviews/compiler-options/index.html
    // `,` is used as plugins separator: `-Xplugin PATHS1,PATHS2`
    // but in SCL-11861 `;` is used
    val pluginSeparator = if (compoundPath.contains(";")) ';' else ','

    val paths = compoundPath.split(pluginSeparator)
    val pathsAbsolute = paths.map(toAbsolutePath(baseDirectory, _, eelDescriptor))
    pathsAbsolute.mkString(pluginSeparator.toString)
  }

  /**
   * Converts `rawPath` to a local path which can be used inside the target machine.
   *
   * @param baseDirectory project's base directory; may have an eel environment prefix, e.g., `$devcontainer.ij/home/user/project`.
   * @param rawPath       path to convert; may be:
   *                       - a relative path (e.g., `target/plugins/foo.jar`),
   *                       - a local absolute path (e.g., `/root/.cache/...` or `C:/Users/...`),
   *                       - an eel-prefixed path (e.g., `$devcontainer.ij/...`).
   * @todo add eel path tests for this method
   */
  private def toAbsolutePath(baseDirectory: String, rawPath: String, eelDescriptor: EelDescriptor): String = {
    // 1) First case: `rawPath` is already absolute and belongs to the target eel descriptor. It can be .e.g, an eel-prefixed path.
    // `Path#isAbsolute` uses host-OS rules, so a path like `/usr/lib/...` in WSL is non-absolute by this method, even though in practice it is.
    // That case is handled by the `EelPath.parse` below.
    val path = Path.of(rawPath)
    val isAbsoluteNio = path.isAbsolute && EelProviderUtil.getEelDescriptor(path) == eelDescriptor
    if (isAbsoluteNio) {
      EelPathUtils.renderAsEelPath(path)
    } else {
      // 2) second case: `EelPath.parse` throws an exception for paths that are not absolute in the target environment, so if it succeeds, we know that
      // `rawPath` is absolute in the target environment and can be returned as-is.
      val isAbsoluteInTargetEnv = Try(EelPath.parse(rawPath, eelDescriptor)).isSuccess
      if (isAbsoluteInTargetEnv) rawPath
      else {
        // 2) Third case: `rawPath` is relative — resolve against `baseDirectory`.
        val resolved = Path.of(baseDirectory).resolve(rawPath)
        EelPathUtils.renderAsEelPath(resolved)
      }
    }
  }
}
