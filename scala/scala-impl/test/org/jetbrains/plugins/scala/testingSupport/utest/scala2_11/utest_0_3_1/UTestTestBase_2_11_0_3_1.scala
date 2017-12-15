package org.jetbrains.plugins.scala.testingSupport.utest.scala2_11.utest_0_3_1

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_11_0_3_1 extends UTestTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("com.lihaoyi" %% "utest" % "0.3.1") :: Nil

}
