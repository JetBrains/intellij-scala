package org.jetbrains.plugins.scala.base.libraryLoaders

import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.util.TestUtils.getIvyCachePath

/**
  * @author adkozlov
  */
trait IvyLibraryLoader extends LibraryLoader {

  import IvyLibraryLoader._

  val name: String
  val vendor: String
  protected val ivyType: IvyType = Jars

  def path(implicit version: ScalaVersion): String =
    s"$getIvyCachePath/$vendor/$folder/$ivyType/$fileName.jar"

  protected def folder(implicit version: ScalaVersion): String

  protected def fileName(implicit version: ScalaVersion): String
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
