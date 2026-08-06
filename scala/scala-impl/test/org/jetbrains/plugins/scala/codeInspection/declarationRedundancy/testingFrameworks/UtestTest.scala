package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class UtestTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("com.lihaoyi" %% "utest" % "0.7.11").transitive())
  )

  def testUtestTestSuiteEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import utest._
       |object UTestTest extends TestSuite { val tests: Tests = Tests{} }
       |""".stripMargin
  )
}
