package org.jetbrains.sbt

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger._

/**
  * @author Nikolay Obedin
  * @since 7/27/15.
  */
trait MockSbtBase extends ScalaSdkOwner {

  implicit val sbtVersion: String

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), IvyManagedLoader(
    "org.scala-lang" % "scala-compiler" % version.minor,
    "org.scala-lang" % "scala-library"  % version.minor,
    "org.scala-lang" % "scala-reflect"  % version.minor,
    "org.scala-sbt" % "sbt" % sbtVersion transitive()
  ))
}

trait MockSbt_0_12 extends MockSbtBase {

  override implicit val version: ScalaVersion = Scala_2_9

  override protected def librariesLoaders: Seq[LibraryLoader] =Seq(HeavyJDKLoader(), IvyManagedLoader(
    "org.scala-lang" % "scala-compiler" % version.minor,
    "org.scala-lang" % "scala-library"  % version.minor,
    "org.scala-sbt" % "sbt" % sbtVersion transitive()
  ))
}

trait MockSbt_0_13 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase {
  override implicit val version: ScalaVersion = Scala_2_12
}
