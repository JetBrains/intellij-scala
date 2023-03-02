package org.jetbrains.sbt

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.{DependencyManagerForSbt, LatestScalaVersions, ScalaVersion}

trait MockSbtBase extends ScalaSdkOwner { this: Test =>

  implicit def sbtVersion: Version

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
    IvyManagedLoader(new DependencyManagerForSbt(sbtVersion), ("org.scala-sbt" % "sbt" % sbtVersion.presentation).transitive())
}

trait MockSbt_0_13 extends MockSbtBase { this: Test =>
  override val sbtVersion: Version = Sbt.Latest_0_13
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_10
}

trait MockSbt_1_0 extends MockSbtBase { this: Test =>
  override val sbtVersion: Version = Sbt.LatestVersion
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12
}