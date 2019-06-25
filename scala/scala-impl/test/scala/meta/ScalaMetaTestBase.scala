package scala.meta

import junit.framework.Test
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}

import scala.meta.intellij.MetaExpansionsManager.META_MINOR_VERSION

trait ScalaMetaTestBase extends ScalaSdkOwner {  this: Test =>

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    IvyManagedLoader("org.scalameta" %% "scalameta" % META_MINOR_VERSION transitive() exclude "com.google.protobuf:protobuf-java")
  )

}