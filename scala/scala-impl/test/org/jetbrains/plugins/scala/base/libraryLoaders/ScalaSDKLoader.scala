package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaCompiler.versionOf
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.{DependencyManager, ScalaLoader}
import org.junit.Assert._

import scala.collection.JavaConverters._


case class ScalaSDKLoader(includeScalaReflect: Boolean = false) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {

    val deps = Seq(
      "org.scala-lang" % "scala-compiler" % version.minor,
      "org.scala-lang" % "scala-library"  % version.minor,
      "org.scala-lang" % "scala-reflect"  % version.minor
    ).filterNot(!includeScalaReflect && _.artId.contains("reflect"))

    val resolved = deps.flatMap(new DependencyManager().resolve(_))

    val srcsResolved = new DependencyManager()
      .resolve("org.scala-lang" % "scala-library" % version.minor % Types.SRC)

    assertEquals(s"Failed to resolve scala sdk version $version, result:\n${resolved.mkString("\n")}",
      deps.size, resolved.size)

    assertTrue(s"Local SDK files failed to verify for version $version:\n${resolved.mkString("\n")}",
      resolved.nonEmpty && resolved.forall(_.file.exists()))

    val library = PsiTestUtil.addProjectLibrary(
      module,
      s"scala-sdk-${version.minor}",
      resolved.map(_.toJarVFile).asJava,
      srcsResolved.map(_.toJarVFile).asJava
    )

    Disposer.register(module, library)

    inWriteAction {
      library.convertToScalaSdkWith(languageLevel(resolved.head.file), resolved.map(_.file))
      module.attach(library)
    }

    ScalaLoader.loadScala()
  }

  private def languageLevel(compiler: File) =
    versionOf(compiler)
      .flatMap(_.toLanguageLevel)
      .getOrElse(ScalaLanguageLevel.Default)
}