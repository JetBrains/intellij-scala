package scala.meta

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

trait ScalaMetaTestBase extends ScalaSdkOwner { this: Test =>
  protected val ScalametaLatestVersion = "4.5.9"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(),
    IvyManagedLoader(("org.scalameta" %% "scalameta" % ScalametaLatestVersion).transitive().exclude("com.google.protobuf:protobuf-java"))
  )
}