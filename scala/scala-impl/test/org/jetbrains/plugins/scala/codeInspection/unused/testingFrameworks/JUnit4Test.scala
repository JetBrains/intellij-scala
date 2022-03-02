package org.jetbrains.plugins.scala.codeInspection.unused.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class JUnit4Test extends ScalaUnusedSymbolInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("junit" % "junit" % "4.13.2").transitive())
  )

  def testJUnit4EntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import org.junit.Test
       |class Foo { @Test def bar() = {} }
       |""".stripMargin
  )
}
