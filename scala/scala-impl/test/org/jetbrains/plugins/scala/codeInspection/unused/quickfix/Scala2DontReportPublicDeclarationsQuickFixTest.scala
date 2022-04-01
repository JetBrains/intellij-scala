package org.jetbrains.plugins.scala.codeInspection.unused.quickfix

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase
import org.junit.Assert.{assertFalse, assertTrue}

class Scala2DontReportPublicDeclarationsQuickFixTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_private_field(): Unit = {
    val  code =
      """
        |@scala.annotation.unused class Foo {
        |  private val s = 0
        |}
      """.stripMargin
    assertFalse(findAllQuickFixes(code, failOnEmptyErrors = false).exists(_.getText == disablePublicDeclarationReporting))
  }

  def test_public_field(): Unit = {
    val  code =
      """
        |@scala.annotation.unused class Foo {
        |  val s = 0
        |}
      """.stripMargin
    assertTrue(doFindQuickFixes(code, disablePublicDeclarationReporting).size == 1)
  }
}
