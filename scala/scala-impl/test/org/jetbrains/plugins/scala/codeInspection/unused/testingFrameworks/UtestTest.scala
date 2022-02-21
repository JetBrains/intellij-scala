package org.jetbrains.plugins.scala.codeInspection.unused.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class UtestTest extends ScalaUnusedSymbolInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("com.lihaoyi" %% "utest" % "0.7.11").transitive())
  )

  def testUtestTestSuiteEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import utest._
       |object UTestTest extends TestSuite { val tests: Tests = Tests{} }
       |""".stripMargin
  )
}
