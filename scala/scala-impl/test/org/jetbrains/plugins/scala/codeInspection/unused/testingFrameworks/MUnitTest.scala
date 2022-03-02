package org.jetbrains.plugins.scala.codeInspection.unused.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class MUnitTest extends ScalaUnusedSymbolInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.scalameta" %% "munit" % "0.7.29").transitive())
  )

  def testMUnitEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |class Foo extends munit.FunSuite{}
       |""".stripMargin
  )
}
