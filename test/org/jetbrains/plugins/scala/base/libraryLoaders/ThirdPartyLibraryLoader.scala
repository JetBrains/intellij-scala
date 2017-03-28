package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.project.ModuleExt

/**
  * @author adkozlov
  */
trait ThirdPartyLibraryLoader extends LibraryLoader {
  protected val name: String

  override def init(implicit version: ScalaVersion): Unit = {
    if (alreadyExistsInModule) return

    val path = this.path
    VfsRootAccess.allowRootAccess(path)
    PsiTestUtil.addLibrary(module, path)

    LibraryLoader.storePointers()
  }

  protected def path(implicit version: ScalaVersion): String

  private def alreadyExistsInModule =
    module.libraries.map(_.getName)
      .contains(name)
}

abstract class IvyLibraryLoaderAdapter extends ThirdPartyLibraryLoader with IvyLibraryLoader {
  protected val version: String

  override protected def folder(implicit version: ScalaVersion): String =
    s"${name}_${version.major}"

  override protected def fileName(implicit version: ScalaVersion): String =
    s"$folder-${this.version}"
}

abstract class ScalaZBaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
  override protected val vendor: String = "org.scalaz"
  override protected val version: String = "7.1.0"
  override protected val ivyType: IvyType = Bundles
}

case class ScalaZCoreLoader(implicit val module: Module) extends ScalaZBaseLoader {
  override protected val name: String = "scalaz-core"
}

case class ScalaZConcurrentLoader(implicit val module: Module) extends ScalaZBaseLoader {
  override protected val name: String = "scalaz-concurrent"
}

case class SlickLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "slick"
  override protected val vendor: String = "com.typesafe.slick"
  override protected val version: String = "3.2.0"
  override protected val ivyType: IvyType = Bundles

  override protected def path(implicit version: ScalaVersion): String =
    super.path(Scala_2_11)
}

case class SprayLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "spray-routing"
  override protected val vendor: String = "io.spray"
  override protected val version: String = "1.3.1"
  override protected val ivyType: IvyType = Bundles

  override protected def path(implicit version: ScalaVersion): String =
    super.path(Scala_2_11)
}

case class CatsLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "cats-core"
  override protected val vendor: String = "org.typelevel"
  override protected val version: String = "0.4.0"

  override protected def path(implicit version: ScalaVersion): String =
    super.path(Scala_2_11)
}

abstract class Specs2BaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
  override protected val vendor: String = "org.specs2"
}

case class Specs2Loader(override protected val version: String)
                       (implicit val module: Module) extends Specs2BaseLoader {
  override protected val name: String = "specs2"
}

case class ScalaCheckLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scalacheck"
  override protected val vendor: String = "org.scalacheck"
  override protected val version: String = "1.12.5"

  override protected def path(implicit version: ScalaVersion): String =
    super.path(Scala_2_11)
}

case class PostgresLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "postgresql"
  override protected val vendor: String = "com.wda.sdbc"
  override protected val version: String = "0.5"

  override protected def path(implicit version: ScalaVersion): String =
    super.path(Scala_2_11)
}

case class ScalaTestLoader(override protected val version: String,
                           override protected val ivyType: IvyType = Jars)
                          (implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scalatest"
  override protected val vendor: String = "org.scalatest"
}

case class ScalaXmlLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scala-xml"
  override protected val vendor: String = "org.scala-lang.modules"
  override protected val version: String = "1.0.1"
  override protected val ivyType: IvyType = Bundles
}

case class ScalacticLoader(override protected val version: String,
                           override protected val ivyType: IvyType = Jars)
                          (implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scalactic"
  override protected val vendor: String = "org.scalactic"
}

case class UTestLoader(override protected val version: String)
                      (implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "utest"
  override protected val vendor: String = "com.lihaoyi"
}

case class QuasiQuotesLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "quasiquotes"
  override protected val vendor: String = "org.scalamacros"
  override protected val version: String = "2.0.0"
}

case class ScalaAsyncLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scala-async"
  override protected val vendor: String = "org.scala-lang.modules"
  override protected val version: String = "0.9.5"
}