package org.jetbrains.plugins.scala.codeInspection.unusedInspections.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspectionTestBase

class MUnitTest extends ScalaUnusedDeclarationInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalameta" %% "munit" % "0.7.29").transitive())
  )

  def testMUnitEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |class Foo extends munit.FunSuite{}
       |""".stripMargin
  )
}
