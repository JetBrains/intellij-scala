package org.jetbrains.sbt

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.project.Version

trait MockSbtBase extends ScalaSdkOwner { this: Test =>

  implicit val sbtVersion: Version

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(
      (scalaJars :+ ("org.scala-sbt" % "sbt" % sbtVersion.presentation).transitive()): _*
    )
  )

  protected def scalaJars: Seq[DependencyDescription] = Seq(
    scalaCompilerDescription,
    scalaLibraryDescription,
    scalaReflectDescription
  )
}

trait MockSbt_0_12 extends MockSbtBase { this: Test =>
  override protected def scalaJars: Seq[DependencyDescription] = Seq(
    scalaCompilerDescription,
    scalaLibraryDescription
  )
}

trait MockSbt_0_13 extends MockSbtBase { this: Test =>
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase { this: Test =>
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12
}
