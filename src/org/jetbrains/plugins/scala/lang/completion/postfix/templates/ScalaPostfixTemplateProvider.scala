package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import java.util

import com.intellij.codeInsight.template.postfix.templates._
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil

/**
 * @author Roman.Shein
 * @since 03.09.2015.
 */
class ScalaPostfixTemplateProvider extends PostfixTemplateProvider {
  override def getTemplates: util.Set[PostfixTemplate] = ScalaPostfixTemplateProvider.templates

  override def isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '!'

  override def preExpand(file: PsiFile, editor: Editor): Unit = {}

  override def preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile

  override def afterExpand(file: PsiFile, editor: Editor): Unit = {}
}

object ScalaPostfixTemplateProvider {
  protected def templates: util.Set[PostfixTemplate] = ContainerUtil.newHashSet(
    new ScalaTryPostfixTemplate,
    new ScalaAssertPostfixTemplate,
    new ScalaCastPostfixTemplate,
    new ScalaElseExpressionPostfixTemplate,
    new ScalaIfExpressionPostfixTemplate,
    new ScalaMatchPostfixTemplate,
    new ScalaForEachPostfixTemplate,
    new ScalaIntroduceFieldPostfixTemplate,
    new ScalaIntorduceVariablePostfixTemplate,
    new ScalaNotPostfixTemplate,
    new ScalaNotPostfixTemplate("!"),
    new ScalaParenthesizedExpressionPostfixTemplate,
    new ScalaReturnPostfixTemplate,
    new ScalaPrintlnPostfixTemplate,
    new ScalaThrowExceptionPostfixTemplate,
    new ScalaWhilePostfixTemplate,
    new ScalaDoWhilePostfixTemplate,
    new ScalaIsNullPostfixTemplate,
    new ScalaNotNullPostfixTemplate,
    new ScalaNotNullPostfixTemplate("nn"),
    new ScalaOptionPostfixTemplate,
    new ScalaSeqPostfixTemplate,
    new ScalaListPostfixTemplate
  )
}
