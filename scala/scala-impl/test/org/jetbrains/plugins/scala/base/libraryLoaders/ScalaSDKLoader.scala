package org.jetbrains.plugins.scala
package base
package libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel, ScalaLibraryProperties, ScalaLibraryType, template}
import org.junit.Assert._

import scala.collection.JavaConverters

case class ScalaSDKLoader(includeScalaReflect: Boolean = false) extends LibraryLoader {

  private object DependencyManager extends DependencyManagerBase {
    override protected val artifactBlackList = Set.empty[String]
  }

  import ScalaSDKLoader._

  def resolveSources(implicit version: debugger.ScalaVersion): VirtualFile = {
    val ResolvedDependency(_, file) = DependencyManager.resolveSingle {
      "org.scala-lang" % "scala-library" % version.minor % Types.SRC
    }
    findJarFile(file)
  }

  override def init(implicit module: Module,
                    version: debugger.ScalaVersion): Unit = {
    val dependencies = for {
      descriptor <- "org.scala-lang" % "scala-compiler" % version.minor ::
        "org.scala-lang" % "scala-library" % version.minor ::
        "org.scala-lang" % "scala-reflect" % version.minor ::
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

    import JavaConverters._
    val classesRoots = compilerClasspath.map(findJarFile).asJava
    val sourceRoots = Seq(resolveSources).asJava

    val library = PsiTestUtil.addProjectLibrary(
      module,
      s"scala-sdk-${version.minor}",
      classesRoots,
      sourceRoots
    )

    Disposer.register(module, library)
    inWriteAction {
      val properties = createLibraryProperties(compilerClasspath)

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

  private def createLibraryProperties(compilerClasspath: Seq[File]) = {
    val properties = new ScalaLibraryProperties()
    properties.compilerClasspath = compilerClasspath
    properties.languageLevel = template.Artifact.ScalaCompiler
      .versionOf(compilerClasspath.head)
      .flatMap(_.toLanguageLevel)
      .getOrElse(ScalaLanguageLevel.Default)
    properties
  }
}