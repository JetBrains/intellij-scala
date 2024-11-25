package org.jetbrains.plugins.scala.injection

abstract class InjectionInBodyTestBase extends ScalaLanguageInjectionTestBase {

  protected val Quotes: String = "\"\"\""
  protected lazy val LanguageAnnotationDef: String = scalaInjectionTestFixture.LanguageAnnotationDef

  protected def doTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""$LanguageAnnotationDef
         |class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTest(languageId: String, text: String, injectedFileExpectedText: String): Unit = {
    val textFinal =
      s"""$LanguageAnnotationDef
         |$text
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, textFinal, injectedFileExpectedText)
  }
}
