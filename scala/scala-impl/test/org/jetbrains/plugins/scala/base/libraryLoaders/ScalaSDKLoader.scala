package org.jetbrains.plugins.scala
package base
package libraryLoaders

import java.io.File
import java.{util => ju}

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel, ScalaLibraryProperties, ScalaLibraryType, template}
import org.junit.Assert._

case class ScalaSDKLoader(includeScalaReflect: Boolean = false) extends LibraryLoader {

  protected lazy val dependencyManager: DependencyManagerBase = new DependencyManagerBase {
    override protected val artifactBlackList: Set[String] = Set.empty
  }

  import DependencyManagerBase._
  import ScalaSDKLoader._
  import template.Artifact

  protected def binaryDependencies(implicit version: ScalaVersion): List[DependencyDescription] =
    version.languageLevel match { // TODO maybe refactoring?
      case ScalaLanguageLevel.Scala_3_0 =>
        List(
          scalaCompilerDescription.transitive(),
          scalaLibraryDescription.transitive(),
          //scalaLibraryDescription(Scala_2_13),
          //DependencyDescription("ch.epfl.lamp", s"tasty-core_${version.major}", version.minor),
          DependencyDescription("ch.epfl.lamp", "dotty-interfaces", version.minor),
          //DependencyDescription("org.scala-lang.modules", "scala-asm", "7.0.0-scala-1")
        )
      case _                  =>
        val maybeScalaReflect = if (includeScalaReflect) Some(scalaReflectDescription) else None
        List(
          scalaCompilerDescription,
          scalaLibraryDescription
        ) ++ maybeScalaReflect
    }

  protected def sourcesDependency(implicit version: ScalaVersion): DependencyDescription =
    scalaLibraryDescription % Types.SRC

  final def sourceRoot(implicit version: ScalaVersion): VirtualFile = {
    val ResolvedDependency(_, file) = dependencyManager.resolveSingle(sourcesDependency)
    findJarFile(file)
  }

  override final def init(implicit module: Module, version: ScalaVersion): Unit = {
    val dependencies = binaryDependencies
    val resolved = dependencyManager.resolve(dependencies: _*)

    if (version.languageLevel == ScalaLanguageLevel.Scala_3_0)
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

    val classesRoots = {
      import scala.collection.JavaConverters._
      compilerClasspath.map(findJarFile).asJava
    }

    val library = PsiTestUtil.addProjectLibrary(
      module,
      s"scala-sdk-${version.minor}",
      classesRoots,
      ju.Collections.singletonList(sourceRoot)
    )

    Disposer.register(module, library)
    inWriteAction {
      val version = Artifact.ScalaCompiler.versionOf(compilerFile)
      val properties = ScalaLibraryProperties(version, compilerClasspath)

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