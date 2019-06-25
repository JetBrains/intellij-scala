package org.jetbrains.plugins.scala
package testingSupport
package specs2
package specs2_2_12_4_0_0

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_12_4_0_0_Base extends Specs2TestCase {

  private val specsVersion: String = "4.0.0"

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
      "org.specs2" %% "specs2-core" % specsVersion,
      "org.specs2" %% "specs2-common" % specsVersion,
      "org.specs2" %% "specs2-matcher" % specsVersion,
      "org.specs2" %% "specs2-fp" % specsVersion,
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  ) :: Nil
}