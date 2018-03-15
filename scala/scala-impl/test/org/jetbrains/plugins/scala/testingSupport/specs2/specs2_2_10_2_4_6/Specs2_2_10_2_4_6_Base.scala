package org.jetbrains.plugins.scala
package testingSupport.specs2.specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_10}
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {

  override implicit val version: ScalaVersion = Scala_2_10

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      "org.specs2" %% "specs2" % "2.4.6",
      "org.scalaz" %% "scalaz-core" % "7.1.0",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.0"
    ) :: Nil
}
