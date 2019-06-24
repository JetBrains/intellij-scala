package org.jetbrains.plugins.scala
package testingSupport
package specs2
package specs2_2_11_3_1M

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_11_3_1_M_Base extends Specs2TestCase {
  private val specsVersion: String = "3.0.1"
  private val scalazVersion = "7.1.0"

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      "org.specs2" %% "specs2-core" % specsVersion,
      "org.specs2" %% "specs2-common" % specsVersion,
      "org.specs2" %% "specs2-matcher" % specsVersion,
      "org.scalaz" %% "scalaz-core" % scalazVersion,
      "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
      "org.scalaz" %% "scalaz-effect" % scalazVersion,
      "org.scalaz.stream" %% "scalaz-stream" % "0.6a",
      "org.scodec" %% "scodec-bits" % "1.1.0",
      "org.scodec" %% "scodec-core" % "1.7.0",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
    ) :: Nil

}