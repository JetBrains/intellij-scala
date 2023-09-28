package scala.meta

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

trait ScalaMetaTestBase { self: TypeInferenceTestBase =>
  protected val ScalametaLatestVersion = "4.5.9"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  override def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalameta" %% "scalameta" % ScalametaLatestVersion).transitive().exclude("com.google.protobuf:protobuf-java"))
  )
}
