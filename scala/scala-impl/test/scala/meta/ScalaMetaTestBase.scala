package scala.meta

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_12}

import scala.meta.intellij.MetaExpansionsManager.META_MINOR_VERSION

trait ScalaMetaTestBase extends ScalaSdkOwner {

  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    IvyManagedLoader("org.scalameta" %% "scalameta" % META_MINOR_VERSION transitive() exclude "com.google.protobuf:protobuf-java")
  )

}