package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.actions.lists.{CommaListSplitJoinContext, JoinOrSplit, ListWithElements}
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

import scala.jdk.CollectionConverters.SeqHasAsJava

sealed abstract class AbstractScalaSplitJoinContext extends CommaListSplitJoinContext {
  protected def getCommonSettings(element: PsiElement): CommonCodeStyleSettings =
    CodeStyle.getLanguageSettings(element.getContainingFile)

  protected def getScalaSettings(element: PsiElement): ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(element.getProject)
}

final class ScalaSplitJoinArgumentsContext extends AbstractScalaSplitJoinContext {
  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent((list: ScArgumentExprList) && Parent(_: ScMethodCall | _: ScConstructorInvocation)) =>
        new ListWithElements(list, list.exprs.asJava)
      case _ => null
    }

  override def needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT &&
      getScalaSettings(firstElement).CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN != ScalaCodeStyleSettings.NO_NEW_LINE

  override def needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT && getCommonSettings(lastElement).CALL_PARAMETERS_RPAREN_ON_NEXT_LINE

  override def getJoinText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.arguments.on.one.line")

  override def getSplitText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.arguments.on.separate.lines")
}

final class ScalaSplitJoinParametersContext extends AbstractScalaSplitJoinContext {
  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent(clause: ScParameterClause) =>
        new ListWithElements(clause, clause.parameters.asJava)
      case _ => null
    }

  override def needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT && getCommonSettings(firstElement).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE

  override def needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT && getCommonSettings(lastElement).METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE

  override def getJoinText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.parameters.on.one.line")

  override def getSplitText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.parameters.on.separate.lines")
}
