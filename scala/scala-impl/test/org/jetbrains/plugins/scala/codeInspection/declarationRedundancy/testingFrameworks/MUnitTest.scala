package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class MUnitTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalameta" %% "munit" % "0.7.29").transitive())
  )

  def testMUnitEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |class Foo extends munit.FunSuite{}
       |""".stripMargin
  )
}
