package org.jetbrains.plugins.scala.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.{Caret, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile, PsiLanguageInjectionHost}
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, InjectionTestFixture}
import org.intellij.plugins.intelliLang
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.injection.ScalaInjectionTestFixture.{ExpectedInjection, ShredInfo, ensureCaretIsSet, pairToTuple}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertNotNull, assertNull, assertTrue, fail}

import java.util
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaInjectionTestFixture(
  project: Project,
  codeInsightFixture: CodeInsightTestFixture
) {
  val injectionTestFixture: InjectionTestFixture = new InjectionTestFixture(codeInsightFixture)

  // using custom language annotation in order it is resolved during unit tests
  val LanguageAnnotationName = "Language"
  val LanguageAnnotationDef = s"class $LanguageAnnotationName(val value: String) extends scala.annotation.StaticAnnotation"
  val intelliLangConfig: intelliLang.Configuration = intelliLang.Configuration.getProjectInstance(project)
  intelliLangConfig.getAdvancedConfiguration.setLanguageAnnotation(LanguageAnnotationName)

  protected def topLevelEditor: Editor = injectionTestFixture.getTopLevelEditor

  protected def topLevelCaretPosition: Int = injectionTestFixture.getTopLevelCaretPosition

  protected def topLevelFile: PsiFile = injectionTestFixture.getTopLevelFile


  /**
   * Copy of [[injectionTestFixture.assertInjectedLangAtCaret]] with a small alteration:<br>
   * if string contains single invalid token it's language can be empty (with id "") so we need to check for parent as well.
   * For example injected regex in "\\".r literal contains single token
   * `PsiElement(INVALID_CHARACTER_ESCAPE_TOKEN)('\\')` but it's parent is `RegExpCharImpl: <\\>`
   */
  def assertInjectedLanguageAtCaret(expectedLanguage: String): Unit = {
    val injectedElement = injectionTestFixture.getInjectedElement
    assertNotNull("Can't find injected element", injectedElement)
    if (expectedLanguage != null) {
      val language = injectedElementLanguage(injectedElement)
      assertNotNull(s"injection of '$expectedLanguage' expected", injectedElement)
      assertEquals(expectedLanguage, language.getID)
    }
    else {
      assertNull(injectedElement)
    }
  }

  def assertHasSomeInjectedLanguageAtCaret(): Unit = {
    val injectedElement = injectionTestFixture.getInjectedElement
    assertNotNull("Can't find injected element", injectedElement)
  }

  private def injectedElementLanguage(injectedElement: PsiElement) = {
    val injectedFile = injectedElement.getContainingFile
    assertTrue("Non-physical injected file is expected", injectedFile.isPhysical)
    //NOTE: we return `injectedFile.getLanguage` instead of `injectedElement.getLanguage` because in case of Scala 3 language
    // only root file will return the correct Scala 3 language instance (`Scala3Language.INSTANCE`)
    // most of other (or even all?) child elements will return Scala 2 version of language (`ScalaLanguage.INSTANCE`)
    injectedFile.getLanguage
  }

  protected def assertInjected(expectedInjection: ExpectedInjection): Unit = {
    val ExpectedInjection(expectedInjectionText, langId, _, _) = expectedInjection

    assertInjectedLanguageAtCaret(langId)

    val foundInjections = injectionTestFixture.getAllInjections.asScala.map(pairToTuple)
    if (foundInjections.isEmpty)
      fail("no language injections found")

    val sameLanguageInjections = foundInjections.filter(_._2.getLanguage.getID == langId).toList
    sameLanguageInjections match {
      case Nil =>
        fail(s"no injection with language `$langId` found")
      case head :: Nil =>
        val (_, injectedFile) = head
        assertSingleInjection(expectedInjection, injectedFile)
      case _ =>
        val withSameText = sameLanguageInjections.find(_._2.textMatches(expectedInjectionText))
        withSameText match {
          case None =>
            val remains = foundInjections
              .map { case (psi, injectedFile) => s"language: ${injectedFile.getLanguage.getID}, host text:'${psi.getText}', injected file: ${injectedFile.getText}" }
              .mkString("\n")

            fail(
              s"""no injection '$expectedInjectionText' -> '$langId' were found
                 |remaining found injections:
                 |$remains""".stripMargin
            )
          case _ =>
        }
    }
  }

  private def assertSingleInjection(
    expectedInjection: ExpectedInjection,
    actualInjectedFile: PsiFile
  ): Unit = {
    val expectedInjectionText = expectedInjection.injectedFileText

    val manager = InjectedLanguageManager.getInstance(project)
    // e.g. if we have a string literal `"\\d\u0025\\u0025".r` the actual regex text will be `\d%\u0025`
    val actualInjectionTextUnescaped = manager.getUnescapedText(actualInjectedFile)

    assertEquals(
      "injected file unescaped text is not equal to the expected one",
      expectedInjectionText,
      actualInjectionTextUnescaped
    )

    expectedInjection.shreds match {
      case Some(expectedShreds) =>
        val host = manager.getInjectionHost(actualInjectedFile.getViewProvider)

        val actualShreds = new ArrayBuffer[ShredInfo]
        manager.enumerate(host, (_, places: util.List[_ <: PsiLanguageInjectionHost.Shred]) => {
          actualShreds ++= places.asScala.map(it => ShredInfo(it.getRange, it.getRangeInsideHost, it.getPrefix, it.getSuffix))
        })

        //adding trailing comma in order to conveniently copy-paste actual result to expected test data
        assertCollectionEquals(
          expectedShreds.sortBy(_.range.getStartOffset).map(_.toString + ","),
          actualShreds.toSeq.sortBy(_.range.getStartOffset).map(_.toString + ",")
        )
      case _ =>
    }

    expectedInjection.isUnparseable match {
      case Some(value) =>
        assertEquals(
          "parseable marker",
          value,
          actualInjectedFile.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION)
        )
      case _ =>
    }
  }

  def doTest(languageId: String, text: String, injectedFileExpectedText: String): Unit = {
    val expectedInjection = ExpectedInjection(
      injectedFileExpectedText.withNormalizedSeparator,
      languageId
    )
    doTest(text, expectedInjection)
  }

  def doTest(text: String, expectedInjection: ExpectedInjection): Unit = {
    codeInsightFixture.configureByText("A.scala", text)
    val file = injectionTestFixture.getTopLevelFile
    ensureCaretIsSet(codeInsightFixture.getEditor, file.asInstanceOf[ScFile])
    assertInjected(expectedInjection)
  }
}

object ScalaInjectionTestFixture {
  private def pairToTuple[A, B](pair: kotlin.Pair[A, B]): (A, B) = (pair.getFirst, pair.getSecond)

  /**
   * @param shreds        None if we are not interested in testing injected string parts
   * @param isUnparseable Some(value) - if we want to check that parser errors will be checked for the injected fragment.
   *                      None - if we are not interested in testing this<br>
   *                      (see JavaDoc for [[com.intellij.lang.injection.InjectedLanguageManager.FRANKENSTEIN_INJECTION]]
   */
  case class ExpectedInjection(
    injectedFileText: String,
    injectedLangId: String,
    shreds: Option[Seq[ShredInfo]] = None,
    isUnparseable: Option[java.lang.Boolean] = None
  )

  /** represents expected data for [[com.intellij.psi.PsiLanguageInjectionHost.Shred]] */
  case class ShredInfo(
    range: TextRange,
    hostRange: TextRange,
    prefix: String = "",
    suffix: String = ""
  ) {
    //overriding `toString` in order when some test fails we can copy actual content and paste it as expected data
    override def toString: String =
      s"ShredInfo($range, $hostRange, \"$prefix\", \"$suffix\")"
  }

  private def ensureCaretIsSet(editor: Editor, file: ScFile): Unit = {
    val caret = editor.getCaretModel.getCurrentCaret
    // test text didn't contain <caret> tag (assuming that it will not be placed at 0 offset in this case)
    val isDefaultCaret = caret.getOffset == 0
    if (isDefaultCaret) {
      placeCaretInsideFirstStringLiteral(caret, file)
    }
  }

  private def placeCaretInsideFirstStringLiteral(caret: Caret, file: ScFile): Unit = {
    val stringLiterals: Seq[ScStringLiteral] = findAllStringLiterals(file)
    stringLiterals match {
      case Seq(literal) =>
        val contentOffset = literal.contentRange.getStartOffset
        caret.moveToOffset(contentOffset)
      case Seq() => Assert.fail("string literal not found")
      case _ => Assert.fail("several string literals were found, use <caret> tag to point to required literal")
    }
  }

  private def findAllStringLiterals(scalaFile: ScFile): Seq[ScStringLiteral] =
    scalaFile.breadthFirst().filterByType[ScStringLiteral].toSeq

}
