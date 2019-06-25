package org.jetbrains.sbt

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_12}

/**
  * @author Nikolay Obedin
  * @since 7/27/15.
  */
trait MockSbtBase extends ScalaSdkOwner { this: Test =>

  implicit val sbtVersion: Version

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), IvyManagedLoader(
    scalaCompilerDescription,
    scalaLibraryDescription,
    scalaReflectDescription,
    "org.scala-sbt" % "sbt" % sbtVersion.presentation transitive()
  ))
}

trait MockSbt_0_12 extends MockSbtBase { this: Test =>

  override protected def librariesLoaders: Seq[LibraryLoader] =Seq(HeavyJDKLoader(), IvyManagedLoader(
    scalaCompilerDescription,
    scalaLibraryDescription,
    "org.scala-sbt" % "sbt" % sbtVersion.presentation transitive()
  ))
}

trait MockSbt_0_13 extends MockSbtBase { this: Test =>
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase { this: Test =>
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12
}
