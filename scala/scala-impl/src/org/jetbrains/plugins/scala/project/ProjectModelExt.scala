package org.jetbrains.plugins.scala.project

import com.intellij.execution.ExecutionException
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.workspace.jps.entities.{DependencyScope, *}
import com.intellij.platform.workspace.storage.{EntitySource, MutableEntityStorage}
import com.intellij.util.PathsList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.external.CompanionProxyUtils
import org.jetbrains.plugins.scala.project.external.CompanionProxyUtils.LegacyBridgeModifiableBaseCompanion
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.sbt.WorkspaceModelUtil

import java.nio.file.Path
import kotlin.Unit.INSTANCE as KUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try

// Project-model extensions (workspace model, external system, Scala SDK classpaths, run configurations).
// They are backend code, so they are not part of the project package object, which the PSI uses.

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

/** The project-model side of [[ModuleExt]]: the Scala SDK library, shared-sources owners, compiler settings import */
implicit class ModuleProjectModelExt(private val module: Module) extends AnyVal {

  def scalaSdk: Option[LibraryEx] =
    module.scalaModuleSettings.flatMap(_.scalaSdk)

  def findJVMModule: Option[Module] = {
    if (module.isJvmModule) {
      Some(module)
    } else if (module.isSharedSourceModule) {
      val sharedSourcesOwnerModules = getSharedSourcesModulesOwners(module)
      sharedSourcesOwnerModules.find(_.isJvmModule)
    } else {
      module.sharedSourceDependency.flatMap(_.findJVMModule)
    }
  }

  /**
   * Selects dependent module for shared-sources module<br>
   * It first search for JVM, then for Js and then for Native
   */
  def findRepresentativeModuleForSharedSourceModule: Option[Module] = cachedInUserData("findRepresentativeModuleForSharedSourceModule", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
    if (module.isSharedSourceModule) {
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

  def externalSystemId: Option[String] =
    module.scalaModuleSettings.flatMap(_ => Option(ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId))

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
