package org.jetbrains.plugins.scala.codeInspection.feature

class ImplicitConversionLanguageFeatureTest extends LanguageFeatureInspectionTestBase {
  override protected val description = "Advanced language feature: implicit conversion"

  def testImportFeatureFlag(): Unit = {
    val before =
      s"""class Aaa {
         |  ${START}implicit$END def i2s(i: Int): String = i.toString
         |}
         |""".stripMargin
    val after =
      s"""import scala.language.implicitConversions
         |
         |class Aaa {
         |  implicit def i2s(i: Int): String = i.toString
         |}
         |""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, hint = "Import feature flag for implicit conversions")
  }
}
