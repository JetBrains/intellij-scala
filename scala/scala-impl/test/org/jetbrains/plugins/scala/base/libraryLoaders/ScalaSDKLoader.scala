package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLibraryProperties, ScalaLibraryType, template}
import org.jetbrains.plugins.scala.{DependencyManager, DependencyManagerBase, ScalaVersion}
import org.junit.Assert._

import java.io.File

/**
 * @param includeScalaReflectIntoCompilerClasspath also see [[ScalaReflectLibraryLoader]]
 * @param includeScalaLibraryTransitiveDependencies for scala 3 library, also includes scala 2 library
 */
case class ScalaSDKLoader(
  includeScalaReflectIntoCompilerClasspath: Boolean = false,
  //TODO: drop this parameter and fix tests
  includeScalaCompilerIntoLibraryClasspath: Boolean = false,
  includeScalaLibraryTransitiveDependencies: Boolean = true,
  includeScalaLibraryFilesInSdk: Boolean = true,
  //TODO: by default sources are not needed in all tests
  // make it "false" by default, check which tests fail and set it to true in those tests
  includeScalaLibrarySources: Boolean = true,
  compilerBridgeBinaryJar: Option[File] = None,
  dependencyManager: DependencyManagerBase = DependencyManager
) extends LibraryLoader {

  import DependencyManagerBase._
  import ScalaSDKLoader._
  import template.Artifact

  def withResolvers(_resolvers: Seq[Resolver]): ScalaSDKLoader = {
    val dependencyManager = new DependencyManagerBase {
      override protected def resolvers: Seq[Resolver] = _resolvers
    }
    copy(dependencyManager = dependencyManager)
  }

  protected def binaryDependencies(implicit version: ScalaVersion): List[DependencyDescription] =
    if (version.languageLevel.isScala3) {
      List(
        scalaCompilerDescription.transitive(),
        if (includeScalaLibraryTransitiveDependencies) scalaLibraryDescription.transitive() else scalaLibraryDescription,
        DependencyDescription("org.scala-lang", "scala3-interfaces", version.minor),
      )
    }
    else {
      val maybeScalaReflect = if (includeScalaReflectIntoCompilerClasspath) Some(scalaReflectDescription) else None
      List(
        scalaCompilerDescription,
        scalaLibraryDescription
      ) ++ maybeScalaReflect
    }

  /**
   * Resolves scala library sources for a given version and returns it's jars.
   * For Scala 3 version it returns two roots - for Scala 2 and Scala 3 libraries
   */
  final def scalaLibrarySources(implicit version: ScalaVersion): Seq[VirtualFile] = {
    val sourceDependency = scalaLibraryDescription % Types.SRC
    val sourceDependencyActual = if (includeScalaLibraryTransitiveDependencies) sourceDependency.transitive() else sourceDependency

    val resolved = dependencyManager.resolve(sourceDependencyActual)
    // This second pass is necessary to resolve Scala 2 library sources, when it's a transitive dependency of a Scala 3 library.
    // For some reason, if I tell Ivy to download dependency sources and set transitive="true" it doesn't download sources for transitive dependencies.
    // Instead, it downloads regular class file jars.
    // As a workaround, I do another pass where I download sources for each such class files jar file independently, non-transitively.
    val resolvedSecondPass = if (resolved.size == 1) resolved else resolved.map(_.info).map(d => dependencyManager.resolveSingle(d.sources()))
    resolvedSecondPass.map(_.file).map(findJarFile)
  }

  override final def init(implicit module: Module, version: ScalaVersion): Unit = {
    val dependencies = binaryDependencies
    val resolved = dependencyManager.resolve(dependencies: _*)

    if (version.isScala3)
      assertTrue(
        s"Failed to resolve scala sdk version $version, result:\n${resolved.mkString("\n")}",
        resolved.size >= dependencies.size
      )
    else
      assertEquals(
        s"Failed to resolve scala sdk version $version, result:\n${resolved.mkString("\n")}",
        dependencies.size,
        resolved.size
      )

    val (resolvedOk, resolvedMissing) = resolved.partition(_.file.exists())
    val compilerClasspath = resolvedOk.map(_.file)

    // Manually resolve a compiler bridge only if it hasn't been provided. This allows testing with a custom bridge.
    val compilerBridge = compilerBridgeBinaryJar.orElse(ScalaSdkUtils.resolveCompilerBridgeJar(version.minor))

    assertTrue(
      s"Some SDK jars were resolved but for some reason do not exist:\n$resolvedMissing",
      resolvedMissing.isEmpty
    )
    assertFalse(
      s"Local SDK files failed to verify for version $version:\n${resolved.mkString("\n")}",
      compilerClasspath.isEmpty
    )

    val compilerFile = compilerClasspath.find(_.getName.contains("compiler")).getOrElse {
      fail(s"Local SDK files should contain compiler jar for : $version\n${compilerClasspath.mkString("\n")}").asInstanceOf[Nothing]
    }

    val scalaLibraryClasses: Seq[VirtualFile] =
      if (includeScalaLibraryFilesInSdk) {
        val files =
          if (includeScalaCompilerIntoLibraryClasspath) compilerClasspath
          else compilerClasspath.filter(_.getName.matches(".*(scala-library|scala3-library).*"))
        files.map(findJarFile)
      }
      else Nil

    val scalaLibrarySourcesActual: Seq[VirtualFile] =
      if (includeScalaLibrarySources) scalaLibrarySources
      else Nil

    val libraryTable = LibraryTablesRegistrar.getInstance.getLibraryTable(module.getProject)
    val scalaSdkName = s"scala-sdk-${version.minor}"

    import scala.jdk.CollectionConverters._

    def createNewLibrary =
      PsiTestUtil.addProjectLibrary(
        module,
        scalaSdkName,
        scalaLibraryClasses.asJava,
        scalaLibrarySourcesActual.asJava
      )

    val library =
      libraryTable.getLibraryByName(scalaSdkName)
        .toOption
        .getOrElse(createNewLibrary)

    inWriteAction {
      val version = Artifact.ScalaCompiler.versionOf(compilerFile)
      val properties = ScalaLibraryProperties(version, compilerClasspath, Seq.empty, compilerBridge)

      val editor = new ExistingLibraryEditor(library, null)
      editor.setType(ScalaLibraryType())
      editor.setProperties(properties)
      editor.commit()

      val model = module.modifiableModel
      model.addLibraryEntry(library)
      model.commit()
    }
  }
}

object ScalaSDKLoader {

  private def findJarFile(file: File) =
    JarFileSystem.getInstance().refreshAndFindFileByPath {
      file.getCanonicalPath + "!/"
    }
}