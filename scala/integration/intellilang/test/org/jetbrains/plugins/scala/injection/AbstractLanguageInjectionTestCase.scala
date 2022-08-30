package org.jetbrains.plugins.scala.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.{Caret, Editor}
import com.intellij.openapi.module.Module
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.intellij.plugins.intelliLang
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.injection.AbstractLanguageInjectionTestCase._
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.Assert
import org.junit.Assert._

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

/** @see AbstractLanguageInjectionTestCase.kt in main IntelliJ repository */
abstract class AbstractLanguageInjectionTestCase extends ScalaLightCodeInsightFixtureTestCase {
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

  /**
   * Copy of [[injectionTestFixture.assertInjectedLangAtCaret]] with a small alteration:<br>
   * if string contains single invalid token it's language can be empty (with id "") so we need to check for parent as well.
   * For example injected regex in "\\".r literal contains single token
   * `PsiElement(INVALID_CHARACTER_ESCAPE_TOKEN)('\\')` but it's parent is `RegExpCharImpl: <\\>`
   */
  private def assertInjectedLangAtCaret(expectedLanguage: String): Unit = {
    val injectedElement = injectionTestFixture.getInjectedElement
    if (expectedLanguage != null) {
      val language = injectedElementLanguage(injectedElement)
      assertNotNull(s"injection of '$expectedLanguage' expected", injectedElement)
      assertEquals(expectedLanguage, language.getID)
    }
    else {
      assertNull(injectedElement)
    }
  }

  private def injectedElementLanguage(injectedElement: PsiElement) = {
    val language = injectedElement.getLanguage match {
      case Language.ANY => injectedElement.getParent.getLanguage
      case lang         => lang
    }
    language
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

    val foundInjections = injectionTestFixture.getAllInjections.asScala.map(pairToTuple)
    if (foundInjections.isEmpty)
      fail("no language injections found")

    val sameLanguageInjections = foundInjections.filter(_._2.getLanguage.getID == langId)
    sameLanguageInjections.toList match {
      case Nil =>
        fail(s"no injection with language `$langId` found")
      case head :: Nil =>
        val injectedFile: PsiFile = head._2
        val manager = InjectedLanguageManager.getInstance(getProject)
        // e.g. if we have a string literal `"\\d\u0025\\u0025".r` the actual regex text will be `\d%\u0025`
        val fileTextUnescaped = manager.getUnescapedText(injectedFile)
        assertEquals("injected file unescaped text is not equal to the expected one", text, fileTextUnescaped)
      case _ =>
        sameLanguageInjections.find(_._2.textMatches(text)) match {
          case None =>
            val remains = foundInjections
              .map { case (psi, injectedFile) => s"${injectedFile.getLanguage.getID}: '${psi.getText}'" }
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
    val file = injectionTestFixture.getTopLevelFile
    ensureCaretIsSet(myFixture.getEditor, file.asInstanceOf[ScFile])
    assertInjected(injectedFileExpectedText, languageId)
  }
}

object AbstractLanguageInjectionTestCase {
  private def pairToTuple[A, B](pair: kotlin.Pair[A, B]): (A, B) = (pair.getFirst, pair.getSecond)

  case class ExpectedInjection(injectedFileText: String, injectedLangId: String)

  private def ensureCaretIsSet(editor: Editor, file: ScFile): Unit = {
    val caret = editor.getCaretModel.getCurrentCaret
    // test text didn't contain <caret> tag (assuming that it will not be placed at 0 offset in this case)
    val isDefaultCaret = caret.getOffset == 0
    if (isDefaultCaret) {
      placeCaretInsideFirstStringLiteral(caret, file)
    }
  }

  private def placeCaretInsideFirstStringLiteral(caret: Caret, file: ScFile): Unit = {
    val stringLiterals: Seq[ScStringLiteral] = findAllStringLiterals(file.asInstanceOf[ScFile])
    stringLiterals match {
      case Seq(literal) =>
        val contentOffset = literal.contentRange.getStartOffset
        caret.moveToOffset(contentOffset)
      case Seq() => Assert.fail("string literal not found")
      case _     => Assert.fail("several string literals were found, use <caret> tag to point to required literal")
    }
  }

  private def findAllStringLiterals(scalaFile: ScFile): Seq[ScStringLiteral] =
    scalaFile.breadthFirst().filterByType[ScStringLiteral].toSeq
}