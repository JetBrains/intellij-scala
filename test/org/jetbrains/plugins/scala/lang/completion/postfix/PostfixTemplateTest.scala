package org.jetbrains.plugins.scala.lang.completion.postfix

import java.io.File

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.ScalaPostfixTemplateProvider
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.junit.Assert._

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
abstract class PostfixTemplateTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def testPath(): String = baseRootPath() + "postfixTemplate/"

  protected def parseTestData(): (String, String) = {
    var fileText: String = FileUtil.loadFile(new File(testPath() + getTestName(true) + ".test"), CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    var separatorIndex = fileText.indexOf("----")
    assert(separatorIndex > 0)
    val inputText = fileText.substring(0, separatorIndex).trim
    separatorIndex += 5
    if (separatorIndex >= fileText.length) return (inputText, "")
    while (fileText.charAt(separatorIndex) == '-' || fileText.charAt(separatorIndex) == '\n') separatorIndex += 1
    (inputText, fileText.substring(separatorIndex).trim)
  }

  protected def getExprAndTemplate(inputText: String): (PostfixTemplate, Int, Int, String) = {
    import scala.collection.JavaConversions._
    val startMarker = "<start>"
    val nameMarker = "<"
    val startOffset = inputText.indexOf(startMarker)
    val nameIndex = inputText.indexOf(nameMarker, startOffset + 7)
    val name = inputText.substring(nameIndex + 1, inputText.indexOf('>', nameIndex))
    val template: PostfixTemplate = ScalaPostfixTemplateProvider.templates.find(_.getKey == "." + name).
      get
    (template, startOffset, nameIndex - 7, inputText.replace("<start>", "").replace(s"<$name>", ""))
  }

  protected def doTest() = {
    val (expectedResult, template, end, expr) = prepareTest()
    assert(template.isApplicable(expr, getFileAdapter.getViewProvider.getDocument, end))
    import org.jetbrains.plugins.scala.extensions._
    inWriteCommandAction(getProjectAdapter) {template.expand(expr, getEditorAdapter)}

    assertEquals(expectedResult, getFileAdapter.getText)
  }

  protected def doNotApplicableTest() = {
    val (_, template, end, expr) = prepareTest()

    assert(!template.isApplicable(expr, getFileAdapter.getViewProvider.getDocument, end))
  }

  protected def prepareTest(): (String, PostfixTemplate, Int, PsiElement) = {
    val (inputTextRaw, expectedResult) = parseTestData()
    val (template, start, end, inputTextProcessed) = getExprAndTemplate(inputTextRaw)
    configureFromFileTextAdapter("dummy.scala", inputTextProcessed)
    val expr = PsiTreeUtil.findElementOfClassAtRange(getFileAdapter, start, end, classOf[ScalaPsiElement])
    assert(expr.getTextRange == new TextRange(start, end))
    val editor = getEditorAdapter
    editor.getCaretModel.moveToOffset(end)
    editor.getSelectionModel.setSelection(start, end)
    (expectedResult, template, end, expr)
  }
}