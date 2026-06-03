package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.workspace.jps.entities.{LibraryEntity, ModuleEntity}
import com.intellij.platform.workspace.storage.{EntitySource, MutableEntityStorage}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.EelAwareDependencyManager
import org.jetbrains.plugins.scala.project.{LibraryBase, LibraryEntityExt, LibraryExt, ModuleEntityExt, MutableEntityStorageExt, ReplClasspath, ScalaLibraryProperties, ScalaLibraryType, Version}

import java.nio.file.Path
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

//noinspection ApiStatus,UnstableApiUsage
object ScalaSdkUtils {

  def configureScalaSdk(
    module: Module,
    compilerVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath,
    sdkPrefix: String,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val scalaSDKLibraryName = scalaSdkLibraryName(sdkPrefix, compilerVersion)
    val projectLibrariesModel = modelsProvider.getModifiableProjectLibrariesModel
    doConfigureScalaSdk(
      libraries = projectLibrariesModel.getLibraries.toSeq,
      isApplicable = (library: Library) => isApplicableScalaSdk(library, scalaSDKLibraryName),
      createLibrary = projectLibrariesModel.createLibrary(scalaSDKLibraryName),
      ensureConvertedToScalaSdk = (library: Library) => ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
        modelsProvider,
        library,
        scalacClasspath,
        scaladocExtraClasspath,
        compilerBridgeBinaryJar,
        replClasspath
      ),
      addToModule = (library: Library) => modelsProvider.getModifiableRootModel(module).addLibraryEntry(library)
    )
  }

  def configureScalaSdk(
    module: ModuleEntity,
    compilerVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath,
    sdkPrefix: String,
    storage: MutableEntityStorage,
    project: Project,
    scalaSdkSourceId: String
  ): Unit =
    configureScalaSdkForModuleEntity(
      module = module,
      compilerVersion = compilerVersion,
      scalacClasspath = scalacClasspath,
      scaladocExtraClasspath = scaladocExtraClasspath,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar,
      replClasspath = replClasspath,
      sdkPrefix = sdkPrefix,
      storage = storage,
      createLibrary = name => storage.addLibraryEntity(name, project, scalaSdkSourceId)
    )

  def configureScalaSdk(
    module: ModuleEntity,
    compilerVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath,
    sdkPrefix: String,
    storage: MutableEntityStorage,
    entitySource: EntitySource
  ): Unit =
    configureScalaSdkForModuleEntity(
      module = module,
      compilerVersion = compilerVersion,
      scalacClasspath = scalacClasspath,
      scaladocExtraClasspath = scaladocExtraClasspath,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar,
      replClasspath = replClasspath,
      sdkPrefix = sdkPrefix,
      storage = storage,
      createLibrary = name => storage.addLibraryEntity(name, Seq.empty, entitySource)
    )

  private def configureScalaSdkForModuleEntity(
    module: ModuleEntity,
    compilerVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath,
    sdkPrefix: String,
    storage: MutableEntityStorage,
    createLibrary: String => LibraryEntity
  ): Unit = {
    val scalaSDKLibraryName = scalaSdkLibraryName(sdkPrefix, compilerVersion)
    doConfigureScalaSdk(
      libraries = storage.entities(classOf[LibraryEntity]).iterator().asScala.toSeq,
      isApplicable = (library: LibraryEntity) => isApplicableScalaSdk(library, scalaSDKLibraryName),
      createLibrary = createLibrary(scalaSDKLibraryName),
      ensureConvertedToScalaSdk = (library: LibraryEntity) => ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
        library,
        storage,
        scalacClasspath,
        scaladocExtraClasspath,
        compilerBridgeBinaryJar,
        replClasspath
      ),
      addToModule = (library: LibraryEntity) => module.addLibraryDependency(storage, library)
    )
  }

  private def isApplicableScalaSdk(library: LibraryBase, scalaSDKLibraryName: String): Boolean =
    library.isScalaSdk && library.name.orNull == scalaSDKLibraryName

  private def scalaSdkLibraryName(sdkPrefix: String, compilerVersion: String): String =
    s"$sdkPrefix: scala-sdk-$compilerVersion"

  private def doConfigureScalaSdk[T] (
    libraries: => Seq[T],
    isApplicable: T => Boolean,
    createLibrary: => T,
    ensureConvertedToScalaSdk: T => Unit,
    addToModule: T => Unit
  ): Unit = {
    val existingScalaSDKForSpecificVersion = libraries.find(isApplicable)
    val scalaSdkLibrary = existingScalaSDKForSpecificVersion.getOrElse(createLibrary)

    ensureConvertedToScalaSdk(scalaSdkLibrary)
    addToModule(scalaSdkLibrary)
  }

  private def ensureScalaLibraryIsConvertedToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath
  ): Unit = {
    val modifiableModel = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    doEnsureScalaLibraryIsConvertedToScalaSdk(
      library,
      modifiableModel.setKind(ScalaLibraryType.Kind),
      properties => modifiableModel.setProperties(properties),
      compilerClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar,
      replClasspath
    )
  }

  private def ensureScalaLibraryIsConvertedToScalaSdk(
    library: LibraryEntity,
    storage: MutableEntityStorage,
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath
  ): Unit =
    doEnsureScalaLibraryIsConvertedToScalaSdk(
      library,
      library.setScalaKind(storage),
      library.setScalaProperties(_, storage),
      compilerClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar,
      replClasspath
    )

  private def doEnsureScalaLibraryIsConvertedToScalaSdk(
    library: LibraryBase,
    setScalaSdkKind: => Unit,
    setProperties: ScalaLibraryProperties => Unit,
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath
  ): Unit = {
    val properties = ScalaLibraryProperties(library.libraryVersion, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar, replClasspath)
    if (!library.isScalaSdk) {
      setScalaSdkKind
    }
    //NOTE: must be called after `setKind` because later resets the properties
    setProperties(properties)
  }

  @deprecated("Use resolveCompilerBridgeJar(EelDescriptor, String) instead", since = "2026.1")
  @Deprecated(since = "2026.1", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def resolveCompilerBridgeJar(scalaVersion: String): Option[Path] =
    resolveCompilerBridgeJar(LocalEelDescriptor.INSTANCE, scalaVersion)

  def resolveCompilerBridgeJar(project: Project, scalaVersion: String): Option[Path] =
    resolveCompilerBridgeJar(EelProviderUtil.getEelDescriptor(project), scalaVersion)

  def resolveCompilerBridgeJar(eelDescriptor: EelDescriptor, scalaVersion: String): Option[Path] =
    compilerBridgeName(scalaVersion)
      .map(name => "org.scala-lang" % name % scalaVersion)
      .map(dep => new EelAwareDependencyManager().resolveSafeAndTransferToRemoteEel(eelDescriptor, dep))
      .flatMap(_.headOption)

  @deprecated("Use resolveReplClasspath(EelDescriptor, ScalaVersion) instead", since = "2026.1")
  @Deprecated(since = "2026.1", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def resolveReplClasspath(scalaVersion: String): ReplClasspath =
    resolveReplClasspath(LocalEelDescriptor.INSTANCE, scalaVersion)

  def resolveReplClasspath(project: Project, scalaVersion: String): ReplClasspath =
    resolveReplClasspath(EelProviderUtil.getEelDescriptor(project), scalaVersion)

  def resolveReplClasspath(eelDescriptor: EelDescriptor, scalaVersion: String): ReplClasspath = {
    val version = Version(scalaVersion)
    if (version.major(2) < Version("3.8")) return ReplClasspath.Bundled
    val dep = ("org.scala-lang" % "scala3-repl_3" % scalaVersion).transitive()
    val paths = new EelAwareDependencyManager().resolveSafeAndTransferToRemoteEel(eelDescriptor, dep)
    ReplClasspath.Provided(paths)
  }

  def compilerBridgeName(scalaVersion: String): Option[String] = {
    val version = Version(scalaVersion)
    if (version.major(1) == Version("2")) {
      if (version >= Version("2.13.12")) {
        // Scala 2.13.12 and later versions distribute their own precompiled compiler bridge with support for
        // compiler diagnostics.
        Some(Scala2CompilerBridgeName)
      } else
        None // Previous Scala 2 versions should use the bundled source based Zinc compiler bridge
    } else Some(Scala3CompilerBridgeName)
  }

  def compilerBridgeJarName(scalaVersion: String): Option[String] =
    compilerBridgeName(scalaVersion).map(n => s"$n-$scalaVersion.jar")

  /**
   * Revert the Scala SDK kind from all existing Scala libraries that shouldn't currently be SDKs.
   * For a detailed explanation of why this method is needed, see `org.jetbrains.sbt.project.data.service.SbtProjectDataService#revertScalaSdkFromLibraries`.
   */
  def revertScalaSdkFromLibraries(modelsProvider: IdeModifiableModelsProvider, externalSystemName: String): Unit = {
    def isFromExternalSource(library: Library): Boolean =
      Option(library.getExternalSource).map(_.getDisplayName).contains(externalSystemName)

    val projectLibraries = modelsProvider.getModifiableProjectLibrariesModel.getLibraries.toSeq
    val moduleLibraries = modelsProvider.getModules.flatMap(modelsProvider.getModifiableRootModel(_).getModuleLibraryTable.getLibraries.toSeq)
    val scalaRuntimeLibraries = (projectLibraries ++ moduleLibraries).filter(_.hasRuntimeLibrary)

    scalaRuntimeLibraries
      .filter { library => library.isScalaSdk && isFromExternalSource(library) && !library.getName.contains("scala-sdk") }
      .foreach { library =>
        val model = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
        model.setKind(null)
      }
  }

  private final val Scala3CompilerBridgeName = "scala3-sbt-bridge"

  private final val Scala2CompilerBridgeName = "scala2-sbt-bridge"
}
