package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader.{ScalaCompilerLoader, ScalaLibraryLoaderAdapter, ScalaReflectLoader, ScalaRuntimeLoader}
import org.jetbrains.plugins.scala.debugger.ScalaVersion_2_10
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Nikolay Obedin
  * @since 7/27/15.
  */
trait MockSbt extends ScalaVersion_2_10 {

  implicit val sbtVersion: String

  protected def addSbtLibrary(implicit module: Module): Unit = {
    import MockSbt._

    val loaders = Seq(ScalaCompilerLoader(), ScalaRuntimeLoader(), ScalaReflectLoader(),
      SbtCollectionsLoader(), SbtInterfaceLoader(), SbtIOLoader(), SbtIvyLoader(), SbtLoggingLoader(),
      SbtMainLoader(), SbtMainSettingsLoader(), SbtProcessLoader(), SbtLoader())

    implicit val version = scalaSdkVersion

    val classPath = loaders.map(urlForLibraryRoot)

    import scala.collection.JavaConversions._
    ModuleRootModificationUtil.addModuleLibrary(module, "sbt", classPath, Seq.empty[String])

    LibraryLoader.storePointers()
  }
}

object MockSbt {

  private def urlForLibraryRoot(loader: ScalaLibraryLoaderAdapter)
                               (implicit version: ScalaSdkVersion): String = {
    val file = new File(loader.path)
    VfsUtil.getUrlForLibraryRoot(file)
  }

  private abstract class SbtBaseLoader(implicit val version: String, val module: Module) extends ScalaLibraryLoaderAdapter {
    override protected val vendor: String = "org.scala-sbt"

    override protected def fileName(implicit version: ScalaSdkVersion): String =
      s"$name-${this.version}"
  }

  private case class SbtCollectionsLoader(implicit override val version: String,
                                          override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "collections"
  }

  private case class SbtInterfaceLoader(implicit override val version: String,
                                        override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "interface"
  }

  private case class SbtIOLoader(implicit override val version: String,
                                 override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "io"
  }

  private case class SbtIvyLoader(implicit override val version: String,
                                  override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "ivy"
  }

  private case class SbtLoggingLoader(implicit override val version: String,
                                      override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "logging"
  }

  private case class SbtMainLoader(implicit override val version: String,
                                   override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "main"
  }

  private case class SbtMainSettingsLoader(implicit override val version: String,
                                           override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "main-settings"
  }

  private case class SbtProcessLoader(implicit implicit override val version: String,
                                      override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "process"
  }

  private case class SbtLoader(implicit override val version: String,
                               override val module: Module) extends SbtBaseLoader {
    override protected val name: String = "sbt"
  }

}
