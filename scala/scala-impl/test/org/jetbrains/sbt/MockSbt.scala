package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader.{ScalaCompilerLoader, ScalaLibraryLoaderAdapter, ScalaReflectLoader, ScalaRuntimeLoader}
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyLibraryLoader, IvyLibraryLoaderAdapter}
import org.jetbrains.plugins.scala.debugger._
import org.jetbrains.sbt.MockSbt._

import scala.collection.JavaConverters._

/**
  * @author Nikolay Obedin
  * @since 7/27/15.
  */
trait MockSbtBase extends ScalaSdkOwner {

  implicit val sbtVersion: String

  protected def scalaLoaders = Seq(ScalaCompilerLoader(), ScalaRuntimeLoader(), ScalaReflectLoader())

  override protected def librariesLoaders: Seq[IvyLibraryLoader]

  override protected def setUpLibraries(): Unit = {
    val classPath = librariesLoaders.map(urlForLibraryRoot)

    ModuleRootModificationUtil.addModuleLibrary(module, "sbt", classPath.asJava, List.empty[String].asJava)
  }
}

trait MockSbt_0_12 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_9

  private val sbt_0_12_modules =
    Seq("sbt","collections","interface","io","ivy","logging","main","process")

  override protected def librariesLoaders: Seq[ScalaLibraryLoaderAdapter] =
    Seq(ScalaCompilerLoader(), ScalaRuntimeLoader()) ++ sbt_0_12_modules.map(sbtLoader)
}

trait MockSbt_0_13 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_10

  private val sbt_0_13_modules =
    Seq("sbt", "collections", "interface", "io", "ivy", "logging", "main", "main-settings", "process")

  override protected def librariesLoaders: Seq[ScalaLibraryLoaderAdapter] =
    scalaLoaders ++ sbt_0_13_modules.map(sbtLoader)
}

trait MockSbt_1_0 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_12

  // https://github.com/sbt/sbt/blob/1.x/project/Dependencies.scala
  // update them when updating versions.sbtVersion
  // TODO find a way to automatically get the dependencies via transitive deps from org.scala-sbt:sbt artifact
  private val ioVersion = "1.0.2"
  private val utilVersion = "1.0.2"
  private val lmVersion = "1.0.3"
  private val zincVersion = "1.0.3"

  private val sbt_1_0_modules = Seq("sbt", "test-agent")

  private val util_cross = Seq("util-cache","util-control","util-logging","util-position","util-relation","util-tracking")
  private val lm_cross = Seq("librarymanagement-core","librarymanagement-ivy")

  private val sbt_1_0_modules_cross = Seq(
    "main","logic","collections","actions","completion",
    "run","task-system","tasks","testing","main-settings",
    "command","protocol","core-macros")

  private def compilerInterfaceLoader(implicit module: Module) = new SbtBaseLoader() {
    override val name: String = "compiler-interface"
    override val version: String = zincVersion
  }

  private def utilInterfaceLoader = new SbtBaseLoader() {
    override val name: String = "util-interface"
    override val version: String = utilVersion
  }

  private def ioLoader = new SbtBaseLoader_Cross() {
    override val name: String = "io"
    override val version: String = ioVersion
  }

  override protected def librariesLoaders: Seq[IvyLibraryLoader] =
    scalaLoaders ++
      sbt_1_0_modules.map(sbtLoader) ++
      sbt_1_0_modules_cross.map(sbtLoader_cross) ++
      util_cross.map(sbtLoader_cross(_)(utilVersion)) ++
      lm_cross.map(sbtLoader_cross(_)(lmVersion)) ++
      Seq(compilerInterfaceLoader, ioLoader, utilInterfaceLoader)
}

private[sbt] object MockSbt {

  def urlForLibraryRoot(loader: IvyLibraryLoader)
                       (implicit version: ScalaVersion): String = {
    val file = new File(loader.path)
    assert(file.exists(), s"library root for ${loader.name} does not exist at $file")
    VfsUtil.getUrlForLibraryRoot(file)
  }

  abstract class SbtBaseLoader(implicit val version: String) extends ScalaLibraryLoaderAdapter {
    override val vendor: String = "org.scala-sbt"

    override def fileName(implicit version: ScalaVersion): String =
      s"$name-${this.version}"
  }

  /** Loads library with cross-versioning. */
  abstract class SbtBaseLoader_Cross(implicit val version: String) extends IvyLibraryLoaderAdapter {
    override val vendor: String = "org.scala-sbt"
  }

  def sbtLoader(libraryName: String)(implicit version: String): SbtBaseLoader =
    new SbtBaseLoader() {
      override val name: String = libraryName
    }

  def sbtLoader_cross(libraryName: String)(implicit version: String): SbtBaseLoader_Cross =
    new SbtBaseLoader_Cross() {
      override val name: String = libraryName
    }


}
