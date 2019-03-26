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
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLibraryProperties, ScalaLibraryType, template}
import org.junit.Assert._

case class ScalaSDKLoader(includeScalaReflect: Boolean = false) extends LibraryLoader {

  private object DependencyManager extends DependencyManagerBase {
    override protected val artifactBlackList = Set.empty[String]
  }

  import DependencyManagerBase._
  import ScalaSDKLoader._
  import debugger.ScalaVersion
  import template.Artifact.ScalaCompiler.{versionOf => ScalaCompilerVersion}

  def sourceRoot(implicit version: ScalaVersion): VirtualFile = {
    val ResolvedDependency(_, file) = DependencyManager.resolveSingle(scalaLibraryDescription % Types.SRC)
    findJarFile(file)
  }

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val dependencies = for {
      descriptor <- scalaCompilerDescription ::
        scalaLibraryDescription ::
        scalaReflectDescription ::
        Nil

      if includeScalaReflect || !descriptor.artId.contains("reflect")
    } yield descriptor

    val resolved = DependencyManager.resolve(dependencies: _*)

    assertEquals(
      s"Failed to resolve scala sdk version $version, result:\n${resolved.mkString("\n")}",
      dependencies.size,
      resolved.size
    )

    val compilerClasspath = for {
      ResolvedDependency(_, file) <- resolved
      if file.exists()
    } yield file

    assertFalse(
      s"Local SDK files failed to verify for version $version:\n${resolved.mkString("\n")}",
      compilerClasspath.isEmpty
    )

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
      val properties = ScalaLibraryProperties.applyByVersion(
        ScalaCompilerVersion(compilerClasspath.head),
        compilerClasspath
      )

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