package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.workspace.jps.entities.{LibraryEntity, ModuleEntity}
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.project.{LibraryBase, LibraryEntityExt, LibraryExt, ModuleEntityExt, MutableEntityStorageExt, ScalaLibraryProperties, ScalaLibraryType, Version}

import java.nio.file.Path
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

object ScalaSdkUtils {

  def configureScalaSdk(
    module: Module,
    compilerVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
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
        compilerBridgeBinaryJar
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
    sdkPrefix: String,
    storage: MutableEntityStorage,
    project: Project,
    scalaSdkSourceId: String
  ): Unit = {
    val scalaSDKLibraryName = scalaSdkLibraryName(sdkPrefix, compilerVersion)
    doConfigureScalaSdk(
      libraries = storage.entities(classOf[LibraryEntity]).iterator().asScala.toSeq,
      isApplicable = (library: LibraryEntity) => isApplicableScalaSdk(library, scalaSDKLibraryName),
      createLibrary = storage.addLibraryEntity(scalaSDKLibraryName, project, scalaSdkSourceId),
      ensureConvertedToScalaSdk = (library: LibraryEntity) => ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
        library,
        storage,
        scalacClasspath,
        scaladocExtraClasspath,
        compilerBridgeBinaryJar
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
  ): Unit = {
    val modifiableModel = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    doEnsureScalaLibraryIsConvertedToScalaSdk(
      library,
      modifiableModel.setKind(ScalaLibraryType.Kind),
      properties => modifiableModel.setProperties(properties),
      compilerClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar
    )
  }

  private def ensureScalaLibraryIsConvertedToScalaSdk(
    library: LibraryEntity,
    storage: MutableEntityStorage,
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
  ): Unit =
    doEnsureScalaLibraryIsConvertedToScalaSdk(
      library,
      library.setScalaKind(storage),
      library.setScalaProperties(_, storage),
      compilerClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar
    )

  private def doEnsureScalaLibraryIsConvertedToScalaSdk(
    library: LibraryBase,
    setScalaSdkKind: => Unit,
    setProperties: ScalaLibraryProperties => Unit,
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
  ): Unit = {
    val properties = ScalaLibraryProperties(library.libraryVersion, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    if (!library.isScalaSdk) {
      setScalaSdkKind
    }
    //NOTE: must be called after `setKind` because later resets the properties
    setProperties(properties)
  }

  def resolveCompilerBridgeJar(scalaVersion: String): Option[Path] =
    compilerBridgeName(scalaVersion)
      .map(name => "org.scala-lang" % name % scalaVersion)
      .flatMap(dep => DependencyManager.resolveSafe(dep).toOption)
      .flatMap(_.headOption)
      .map(_.file)

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
   * For a detailed explanation of why this method is needed, see [[org.jetbrains.sbt.project.data.service.SbtProjectDataService#revertScalaSdkFromLibraries]].
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
