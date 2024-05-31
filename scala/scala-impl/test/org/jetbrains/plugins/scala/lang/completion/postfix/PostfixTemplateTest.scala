package org.jetbrains.plugins.scala
package lang
package completion
package postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithAllIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.io.File

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
@Category(Array(classOf[CompletionTests]))
abstract class PostfixTemplateTest extends ScalaLightCodeInsightFixtureTestCase {

  import PostfixTemplateTest._

  def testPath(): String = TestUtils.getTestDataPath + "/postfixTemplate/"

  protected final def parseTestData(): (String, String) = {
    var fileText: String = FileUtil.loadFile(new File(testPath() + getTestName(true) + ".test"), CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    var separatorIndex = fileText.indexOf("----")
    assertTrue(separatorIndex > 0)
    val inputText = fileText.substring(0, separatorIndex).trim
    separatorIndex += 5
    if (separatorIndex >= fileText.length) return (inputText, "")
    while (fileText.charAt(separatorIndex) == '-' || fileText.charAt(separatorIndex) == '\n') separatorIndex += 1
    (inputText, fileText.substring(separatorIndex).trim)
  }

  protected final def doTest(): Unit = {
    val (inputText, expected) = parseTestData()
    val descriptor = prepareTest(inputText)

    val file = getFile
    assertTrue(descriptor.expand(file, getEditor))
    assertEquals(expected, file.getText)
  }

  protected final def doNotApplicableTest(): Unit = {
    val (inputText, _) = parseTestData()
    assertFalse(prepareTest(inputText).expand(getFile))
  }

  private def prepareTest(inputText: String): TemplateDescriptor = {
    val (range, name) = extractName(inputText)

    val fileText = inputText.replace(StartMarker, "")
      .replace(TagStart + name + TagEnd, "")

    myFixture.configureByText("dummy.scala", fileText)
    updateModels(getEditor, range)

    TemplateDescriptor(Templates(name), range)
  }
}

object PostfixTemplateTest {

  private val StartMarker = "<start>"
  private val TagStart = "<"
  private val TagEnd = ">"

  private val Templates = {
    import templates.ScalaExhaustiveMatchPostfixTemplate
    ScalaPostfixTemplateProvider.Templates.map {
      case template: ScalaExhaustiveMatchPostfixTemplate => ScalaExhaustiveMatchPostfixTemplate.exhaustiveAlias -> template
      case template => template.getPresentableName -> template
    }.toMap
  }

  private case class TemplateDescriptor(template: PostfixTemplate, range: TextRange) {

    def expand(file: PsiFile, editor: Editor = null): Boolean = {
      val startOffset = range.getStartOffset
      val endOffset = range.getEndOffset

      val elements = ScalaPsiUtil.elementsAtRange[ScalaPsiElement](file, startOffset, endOffset)
      assertTrue(s"No Scala PSI elements found at range $range", elements.nonEmpty)
      assertEquals(range, elements.head.getTextRange)

      val document = file.getFileDocument
      val applicableElement = elements.find(template.isApplicable(_, document, endOffset))

      applicableElement match {
        case Some(element) =>
          inWriteCommandAction(template.expand(element, editor))(null)
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
          true
        case None => false
      }
    }
  }

  private def extractName(inputText: String): (TextRange, String) = {
    val startOffset = inputText.indexOf(StartMarker)
    val nameStartIndex = inputText.indexOf(TagStart, startOffset + StartMarker.length)
    val nameEndIndex = inputText.indexOf(TagEnd, nameStartIndex)

    val name = inputText.substring(nameStartIndex + 1, nameEndIndex)
    (new TextRange(startOffset, nameStartIndex - StartMarker.length), name)
  }

  private def updateModels(editor: Editor, range: TextRange): Unit = {
    val endOffset = range.getEndOffset

    editor.getCaretModel.moveToOffset(endOffset)
    editor.getSelectionModel.setSelection(range.getStartOffset, endOffset)
  }
}