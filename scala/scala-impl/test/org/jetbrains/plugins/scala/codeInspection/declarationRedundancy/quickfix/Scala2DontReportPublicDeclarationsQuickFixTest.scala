package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase
import org.junit.Assert.assertFalse

class Scala2DontReportPublicDeclarationsQuickFixTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_private_field(): Unit = {
    val code =
      """
        |@scala.annotation.unused class Foo {
        |  private val s = 0
        |}
      """.stripMargin
    val allQuickFixes = findAllQuickFixes(code, failOnEmptyErrors = false)
    val quickFixFound = allQuickFixes.exists(_.getText == disablePublicDeclarationReporting)
    val message =
      s"""
         |QuickFix '$disablePublicDeclarationReporting' was found, even though the code has no unused public declarations:
         |$code
         |""".stripMargin
    assertFalse(message, quickFixFound)
  }

  def test_public_field(): Unit = {
    val code =
      """
        |@scala.annotation.unused class Foo {
        |  val s = 0
        |}
      """.stripMargin
    doFindQuickFixes(code, disablePublicDeclarationReporting)
  }
}
