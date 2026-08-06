package org.jetbrains.plugins.scala.util.dependencymanager

import org.jetbrains.plugins.scala.DependencyManagerBase

// from Michael M.: this blacklist is in order that tested libraries do not transitively fetch `scala-library`,
// which is loaded in a special way in tests via org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
//TODO: should we add scala3-* here?
object TestDependencyManagers {
  object IgnoringScalaLibrary extends DependencyManagerBase {
    override val artifactBlackList: Set[String] = Set("scala-library")
  }

  object IgnoringAllScalaArtifacts extends DependencyManagerBase {
    override val artifactBlackList: Set[String] = Set("scala-library", "scala-reflect", "scala-compiler")
  }
}
