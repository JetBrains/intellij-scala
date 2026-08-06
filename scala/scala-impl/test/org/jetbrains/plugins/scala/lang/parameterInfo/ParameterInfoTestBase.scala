package org.jetbrains.plugins.scala.lang.parameterInfo

import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.junit.Assert._
import org.junit.ComparisonFailure

import java.awt.Color
import scala.collection.mutable

/**
 * Place expected test data in the comment at the end of a file.
 * Test data format per entry: `TEXT: *paramInfoText*, STRIKEOUT: *boolean*`
 *
 * Example: {{{
 * def foo(x: Int) = 1
 * @deprecated def foo(x: Boolean) = 2
 * foo(<caret>)
 * /*
 * TEXT: x: Boolean, STRIKEOUT: true
 * TEXT: x: Int, STRIKEOUT: false
 * */
 * }}}
 *
 * Use `NO_ELEMENTS` if nothing is expected to be shown
 */
abstract class ParameterInfoTestBase[Owner <: PsiElement] extends ScalaLightCodeInsightFixtureTestCase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}parameterInfo/"

  protected def createHandler: ParameterInfoHandlerWithTabActionSupport[Owner, Any, _ <: PsiElement]

  import ParameterInfoTestBase._

  protected def configureFile(): PsiFile = {
    val filePath = s"${getTestName(false)}.scala"
    myFixture.configureByFile(filePath)
  }

  protected final def doTest(testUpdate: Boolean = true): Unit = {
    val file = configureFile()
    val caretOffset = myFixture.getCaretOffset

    val context = new ShowParameterInfoContext(getEditor, getProject, file, caretOffset, -1)
    val handler = createHandler

    val actual: Seq[String] = handleUI(handler, context)
    val expected: Seq[Seq[String]] = expectedSignatures(getFile)

    assertNotNull(expected)
    if (!expected.contains(actual)) {
      throw new ComparisonFailure("signatures don't match", expected.flatten.mkString("\n"), actual.mkString("\n"))
    }

    if (testUpdate && actual.nonEmpty && actual != Seq(ParameterInfoResult.NoElements)) {
      //todo test correct parameter index after moving caret
      val actualAfterUpdate = handleUpdateUI(handler, context)
      assertTrue(expected.contains(actualAfterUpdate))
    }
  }

  private def handleUI(handler: ParameterInfoHandler[Owner, Any],
                       context: CreateParameterInfoContext): Seq[String] = {
    val parameterOwner = handler.findElementForParameterInfo(context)
    val items = Option(context.getItemsToShow).getOrElse(Array.empty).toIndexedSeq
    uiStrings(items, handler, parameterOwner)
  }

  private def handleUpdateUI(handler: ParameterInfoHandler[Owner, Any],
                             context: CreateParameterInfoContext): Seq[String] = {
    val updatedContext = updateContext(context)
    val parameterOwner = handler.findElementForUpdatingParameterInfo(updatedContext)

    updatedContext.setParameterOwner(parameterOwner)
    handler.updateParameterInfo(parameterOwner, updatedContext)

    uiStrings(updatedContext.getObjectsToView.toIndexedSeq, handler, parameterOwner)
  }

  private def updateContext(context: CreateParameterInfoContext): UpdateParameterInfoContext = {
    val itemsToShow = context.getItemsToShow
    new MockUpdateParameterInfoContext(getEditor, getFile, itemsToShow) {
      private var items: Array[AnyRef] = itemsToShow

      override def getObjectsToView: Array[AnyRef] = items

      override def removeHint(): Unit = {
        items = Array.empty
      }
    }
  }
}

object ParameterInfoTestBase {

  // TODO: check background color and whether it is disabled
  private[parameterInfo] final case class ParameterInfoResult(text: String, disabled: Boolean, strikeout: Boolean, backgroundColor: Color) {

    import ParameterInfoResult._

    override def toString: String = s"$TextField: $text, $StrikeoutField: $strikeout"
  }

  private[parameterInfo] object ParameterInfoResult {
    val TextField = "TEXT"
    val StrikeoutField = "STRIKEOUT"

    val NoElements = "NO_ELEMENTS"
  }

  private def uiStrings[Owner <: PsiElement](items: Seq[AnyRef],
                                             handler: ParameterInfoHandler[Owner, Any],
                                             parameterOwner: Owner): Seq[String] = {
    val result = mutable.SortedSet.empty[String]
    items.foreach { item =>
      val uiContext = createInfoUIContext(parameterOwner) {
        result += _
      }
      handler.updateUI(item, uiContext)
    }

    if (result.isEmpty) result += ParameterInfoResult.NoElements

    result.toSeq.flatMap(normalize)
  }

  private[this] def createInfoUIContext[Owner <: PsiElement](parameterOwner: Owner)
                                                            (consume: String => Unit) = new ParameterInfoUIContext {
    override def getParameterOwner: PsiElement = parameterOwner

    override def setupUIComponentPresentation(text: String, highlightStartOffset: Int, highlightEndOffset: Int,
                                              isDisabled: Boolean, strikeout: Boolean, isDisabledBeforeHighlight: Boolean,
                                              background: Color): String = {
      val result = ParameterInfoResult(text = text, disabled = isDisabled, strikeout = strikeout, backgroundColor = background)
      consume(result.toString)
      text
    }

    override def getDefaultParameterColor: Color = HintUtil.getInformationColor

    override def isUIComponentEnabled: Boolean = false

    override def getCurrentParameterIndex: Int = 0

    override def setUIComponentEnabled(enabled: Boolean): Unit = {}

    override def isSingleParameterInfo = false

    override def isSingleOverload = false

    override def setupRawUIComponentPresentation(htmlText: String): Unit = {}
  }

  private def expectedSignatures(file: PsiFile): Seq[Seq[String]] = {
    val ExpectedResultFromLastComment(_, commentText) = TestUtils.extractExpectedResultFromLastComment(file)
    val values = commentText.split("<--->")
    val valuesNormalized = values.map(normalize)
    valuesNormalized.toIndexedSeq
  }

  private[this] def normalize(string: String) =
    StringUtil.convertLineSeparators(string)
      .split('\n')
      .map(_.trim)
      .filterNot(_.isEmpty)
      .toSeq
}
