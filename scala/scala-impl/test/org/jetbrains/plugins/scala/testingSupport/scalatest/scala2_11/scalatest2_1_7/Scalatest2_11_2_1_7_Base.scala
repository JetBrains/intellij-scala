package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest2_1_7

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 22.01.2015
 */
abstract class Scalatest2_11_2_1_7_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
      "org.scalatest" %% "scalatest" % "2.1.7",
    ) :: Nil

}
