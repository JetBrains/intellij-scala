package org.jetbrains.plugins.scala.codeInspection.unused.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class ScalaTest3Test extends ScalaUnusedSymbolInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % "3.1.1").transitive())
  )

  def testScalaTest3TestSuiteEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import org.scalatest.TestSuite
       |class Foo extends TestSuite {}
       |""".stripMargin
  )
}
