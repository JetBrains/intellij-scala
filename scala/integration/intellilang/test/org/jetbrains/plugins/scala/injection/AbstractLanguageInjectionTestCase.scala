package org.jetbrains.plugins.scala.injection

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.intellij.plugins.intelliLang
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.injection.AbstractLanguageInjectionTestCase._
import org.junit.Assert._

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

/** @see AbstractLanguageInjectionTestCase.kt in main IntelliJ repository */
abstract class AbstractLanguageInjectionTestCase extends ScalaLightCodeInsightFixtureTestAdapter {
  private var injectionTestFixture: InjectionTestFixture = _
  protected var intelliLangConfig: intelliLang.Configuration = _

  protected def topLevelEditor: Editor = injectionTestFixture.getTopLevelEditor
  protected def topLevelCaretPosition: Int = injectionTestFixture.getTopLevelCaretPosition
  protected def topLevelFile: PsiFile = injectionTestFixture.getTopLevelFile

  // using custom language annotation in order it is resolved during unit tests
  protected val LanguageAnnotationName = "Language"
  protected val LanguageAnnotationDef = s"class $LanguageAnnotationName(val value: String) extends scala.annotation.StaticAnnotation"

  override def setUp(): Unit = {
    super.setUp()
    injectionTestFixture = new InjectionTestFixture(myFixture)
  }

  override def setUpLibraries(implicit module: Module): Unit = {
    super.setUpLibraries
    intelliLangConfig = intelliLang.Configuration.getProjectInstance(module.getProject)
    intelliLangConfig.getAdvancedConfiguration.setLanguageAnnotation(LanguageAnnotationName)
  }

  protected def assertInjectedLangAtCaret(lang: String): Unit = {
    injectionTestFixture.assertInjectedLangAtCaret(lang)
  }

  protected def assertInjected(injectedFileText: String, injectedLangId: String): Unit = {
    assertInjectedLangAtCaret(injectedLangId)
    val expected = ExpectedInjection(
      injectedFileText.withNormalizedSeparator,
      injectedLangId
    )
    assertInjected(expected)
  }

  protected def assertInjected(expectedInjection: ExpectedInjection): Unit = {
    val ExpectedInjection(text, langId) = expectedInjection

    val foundInjections = injectionTestFixture.getAllInjections.asScala.map(pairToTuple).toBuffer
    if (foundInjections.isEmpty)
      fail("no language injections found")

    val sameLanguageInjections = foundInjections.filter(_._2.getLanguage.getID == langId)
    sameLanguageInjections.toList match {
      case Nil =>
        fail(s"no injection with language `$langId` found")
      case head :: Nil =>
        val injectedFile: PsiFile = head._2
        val fileText = injectedFile.getText
        assertEquals("injected file text is not equal to the expected one", text, fileText)
      case _ =>
        sameLanguageInjections.find(_._2.textMatches(text)) match {
          case None =>
            val remains = foundInjections
              .map { case (psi, injectedFile) => s"'${psi.getText}' -> '${injectedFile.getLanguage.getID}'" }
              .mkString("\n")

            fail(
              s"""no injection '$text' -> '$langId' were found
                 |remaining found injections:
                 |$remains""".stripMargin
            )
          case _ =>
        }
    }
  }

  protected def doTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""$LanguageAnnotationDef
         |class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTest(languageId: String, text: String, injectedFileExpectedText: String): Unit = {
    val textFinal =
      s"""$LanguageAnnotationDef
         |$text
         |""".stripMargin
    doTest(languageId, textFinal, injectedFileExpectedText)
  }

  protected def doTest(languageId: String, text: String, injectedFileExpectedText: String): Unit = {
    myFixture.configureByText("A.scala", text)
    assertInjected(injectedFileExpectedText, languageId)
  }
}

object AbstractLanguageInjectionTestCase {
  private def pairToTuple[A, B](pair: kotlin.Pair[A, B]): (A, B) = (pair.getFirst, pair.getSecond)

  case class ExpectedInjection(injectedFileText: String, injectedLangId: String)
}