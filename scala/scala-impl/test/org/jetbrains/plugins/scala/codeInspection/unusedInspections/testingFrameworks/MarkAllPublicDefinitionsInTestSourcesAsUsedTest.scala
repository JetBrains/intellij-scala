package org.jetbrains.plugins.scala.codeInspection.unusedInspections.testingFrameworks

import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspectionTestBase

class MarkAllPublicDefinitionsInTestSourcesAsUsedTest extends ScalaUnusedDeclarationInspectionTestBase {

  override def placeSourceFilesInTestContentRoot: Boolean = true

  def test_unused_public_definitions_in_test_source_root(): Unit = checkTextHasNoErrors(
    s"""
       |class Foo {
       |  def bar(): Unit = {}
       |}
       |""".stripMargin
  )
  
}



