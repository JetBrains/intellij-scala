package org.jetbrains.sbt

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger._
import org.jetbrains.plugins.scala.project.Version

/**
  * @author Nikolay Obedin
  * @since 7/27/15.
  */
trait MockSbtBase extends ScalaSdkOwner {

  implicit val sbtVersion: Version

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), IvyManagedLoader(
    scalaCompilerDescription,
    scalaLibraryDescription,
    scalaReflectDescription,
    "org.scala-sbt" % "sbt" % sbtVersion.presentation transitive()
  ))
}

trait MockSbt_0_12 extends MockSbtBase {

  override implicit val version: ScalaVersion = Scala_2_9

  override protected def librariesLoaders: Seq[LibraryLoader] =Seq(HeavyJDKLoader(), IvyManagedLoader(
    scalaCompilerDescription,
    scalaLibraryDescription,
    "org.scala-sbt" % "sbt" % sbtVersion.presentation transitive()
  ))
}

trait MockSbt_0_13 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_12
}
