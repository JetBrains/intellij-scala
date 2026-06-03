package org.jetbrains.sbt

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagerForSbt
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

trait MockSbtBase extends ScalaSdkOwner { this: Test =>

  implicit def sbtVersion: SbtVersion

  override protected def buildVersionsDetailsMessage: String = {
    super.buildVersionsDetailsMessage + s", sbt: $sbtVersion"
  }

  protected def scalaSdkLoader: ScalaSDKLoader = ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true)

  override def librariesLoaders: Seq[LibraryLoader] =
    Seq(
      scalaSdkLoader,
      sbtLoader
    )

  private def sbtLoader: IvyManagedLoader =
    IvyManagedLoader(new TestDependencyManagerForSbt(sbtVersion), ("org.scala-sbt" % "sbt" % sbtVersion.minor).transitive())
}

trait MockSbt_0_13 extends MockSbtBase { this: Test =>
  override val sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_0_13
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase { this: Test =>
  override val sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12
}

trait MockSbt_2 extends MockSbtBase { this: Test =>
  override val sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_2

  // Q: hmm, why defining a scala version is even here?
  // Sbt version should be independent of the scala version used in the project
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}