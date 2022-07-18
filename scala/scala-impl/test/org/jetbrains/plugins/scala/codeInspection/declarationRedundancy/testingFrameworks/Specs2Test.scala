package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.testingFrameworks

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.LibrariesOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Specs2Test extends ScalaUnusedDeclarationInspectionTestBase with LibrariesOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.specs2" %% "specs2-core" % "4.13.2").transitive())
  )

  def testSpecs2SpecificationEntryPoint(): Unit = checkTextHasNoErrors(
    s"""
       |import org.specs2.mutable._
       |class Foo extends Specification {}
       |""".stripMargin
  )
}
