package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest3_0_1

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 10.03.2017
 */
abstract class Scalatest2_11_3_0_1_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
      "org.scalatest" %% "scalatest" % "3.0.1",
      "org.scalactic" %% "scalactic" % "3.0.1"
    ) :: Nil

}
