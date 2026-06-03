package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class JUnit4Test extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("junit" % "junit" % "4.13.2").transitive())
  )

  def testJUnit4EntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import org.junit.Test
       |class Foo { @Test def bar() = {} }
       |""".stripMargin
  )
}
