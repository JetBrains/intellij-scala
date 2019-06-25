package org.jetbrains.plugins.scala
package testingSupport
package specs2
package specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      "org.specs2" %% "specs2" % "2.4.6",
      "org.scalaz" %% "scalaz-core" % "7.1.0",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.0"
    ) :: Nil
}
