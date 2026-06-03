package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class JUnit5UsedTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.junit.jupiter" % "junit-jupiter" % "5.8.1").transitive())
  )

  def testJUnit5EntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import org.junit.jupiter.api.Test
       |class Foo { @Test def bar() = {} }
       |""".stripMargin
  )
}
