package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion._2_11
import org.jetbrains.plugins.scala.util.TestUtils._

/**
  * @author adkozlov
  */
trait ThirdPartyLibraryLoader extends LibraryLoader {
  protected val name: String

  override def init(implicit version: ScalaSdkVersion): Unit = {
    if (alreadyExistsInModule) return

    val path = this.path
    VfsRootAccess.allowRootAccess(path)
    PsiTestUtil.addLibrary(module, path)

    LibraryLoader.storePointers()
  }

  protected def path(implicit version: ScalaSdkVersion): String

  private def alreadyExistsInModule =
    module.libraries.map(_.getName)
      .contains(name)
}

abstract class IvyLibraryLoaderAdapter extends ThirdPartyLibraryLoader {
  protected val vendor: String
  protected val version: String
  protected val isBundled: Boolean = false

  protected def path(implicit version: ScalaSdkVersion): String = {
    val major = version.getMajor

    val path = s"$vendor/${name}_$major/${if (isBundled) "bundles" else "jars"}"
    val fileName = s"${name}_$major-${this.version}"

    s"$getIvyCachePath/$path/$fileName.jar"
  }
}

case class ScalaZLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scalaz-core"
  override protected val vendor: String = "org.scalaz"
  override protected val version: String = "7.1.0"
  override protected val isBundled: Boolean = true
}

case class SlickLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "slick"
  override protected val vendor: String = "com.typesafe.slick"
  override protected val version: String = "3.1.0"
  override protected val isBundled: Boolean = true

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}

case class SprayLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "spray-routing"
  override protected val vendor: String = "io.spray"
  override protected val version: String = "1.3.1"
  override protected val isBundled: Boolean = true

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}

case class CatsLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "cats-core"
  override protected val vendor: String = "org.typelevel"
  override protected val version: String = "0.4.0"

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}

case class Specs2Loader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "specs2"
  override protected val vendor: String = "org.specs2"
  override protected val version: String = "2.4.15"

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}

case class ScalaCheckLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "scalacheck"
  override protected val vendor: String = "org.scalacheck"
  override protected val version: String = "1.12.5"

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}

case class PostgresLoader(implicit val module: Module) extends IvyLibraryLoaderAdapter {
  override protected val name: String = "postgresql"
  override protected val vendor: String = "com.wda.sdbc"
  override protected val version: String = "0.5"

  override protected def path(implicit version: ScalaSdkVersion): String =
    super.path(_2_11)
}
