package org.jetbrains.plugins.scala.intelliLang.injection

import org.intellij.lang.annotations.Language

abstract class InjectionInBodyTestBase extends ScalaLanguageInjectionTestBase {

  protected val Quotes: String = "\"\"\""
  protected lazy val LanguageAnnotationDef: String = scalaInjectionTestFixture.LanguageAnnotationDef

  override protected def setUp(): Unit = {
    super.setUp()

    // NOTE: this doesn't mean that language injection won't work in the test.
    // I set "caresAboutInjection" to false not to mute the behavior ofCodeInsightTestFixtureImpl.setupEditorForInjectedLanguage.
    // That method replaces the fixture file and editor with the injected file editor window and synthetic file.
    // We don't rely on that functionality and search injections by ourselves
    myFixture.setCaresAboutInjection(false)
  }

  protected def doTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTestInBody(
    languageId: String,
    @Language("Scala") classBody: String,
    injectedFileExpectedText: String
  ): Unit = {
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
