package org.jetbrains.plugins.scala.base.libraryLoaders

import org.jetbrains.plugins.scala.util.TestUtils.{ScalaSdkVersion, getIvyCachePath}

/**
  * @author adkozlov
  */
trait IvyLibraryLoader extends LibraryLoader {

  import IvyLibraryLoader._

  protected val name: String
  protected val vendor: String
  protected val ivyType: IvyType = Jars

  protected def path(implicit version: ScalaSdkVersion): String =
    s"$getIvyCachePath/$vendor/$folder/$ivyType/$fileName.jar"

  protected def folder(implicit version: ScalaSdkVersion): String

  protected def fileName(implicit version: ScalaSdkVersion): String
}

object IvyLibraryLoader {

  sealed trait IvyType

  case object Bundles extends IvyType {
    override def toString: String = "bundles"
  }

  case object Jars extends IvyType {
    override def toString: String = "jars"
  }

  case object Sources extends IvyType {
    override def toString: String = "srcs"
  }

}
