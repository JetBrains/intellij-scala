package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.actions.lists.{CommaListSplitJoinContext, JoinOrSplit, ListWithElements}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import kotlin.Pair
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTupleTypeElement, ScTypeArgs, ScTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScTuple}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParamClause}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

sealed abstract class AbstractScalaSplitJoinContext extends CommaListSplitJoinContext {
  protected def kind: String

  protected def getCommonSettings(element: PsiElement): CommonCodeStyleSettings =
    CodeStyle.getLanguageSettings(element.getContainingFile)

  protected def getScalaSettings(element: PsiElement): ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(element.getProject)

  override def needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT

  override def needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    mode == JoinOrSplit.SPLIT

  override final def getJoinText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.on.one.line", kind)

  override final def getSplitText(data: ListWithElements): String =
    ScalaBundle.message("intention.family.put.on.separate.lines", kind)
}

final class ScalaSplitJoinArgumentsContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "arguments"

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
}

final class ScalaSplitJoinParametersContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "parameters"

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
}

final class ScalaSplitJoinTupleTypesContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "tuple type elements"

  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent((_: ScTypes) && Parent(tupleType: ScTupleTypeElement)) =>
        new ListWithElements(tupleType, tupleType.components.asJava)
      case Parent(tupleType: ScTupleTypeElement) =>
        new ListWithElements(tupleType, tupleType.components.asJava)
      case _ => null
    }

  /* Workaround to properly handle line breaks after `(` when joining lines.
   * Since tuple type components are not direct children of ScTupleTypeElement
   * and firstElement.prevSibling is null.
   */
  override protected def addHeadReplacementsForJoining(data: ListWithElements,
                                                       replacements: util.List[Pair[TextRange, String]],
                                                       firstElement: PsiElement): Unit =
    data.getList match {
      case tupleType: ScTupleTypeElement =>
        super.addHeadReplacementsForJoining(data, replacements, tupleType.typeList)
      case _ =>
    }

  /* Workaround to properly handle line breaks before `)` when joining lines.
   * Since tuple type components are not direct children of ScTupleTypeElement
   * and lastElement.nextSibling is null.
   */
  override protected def addTailReplacementsForJoining(data: ListWithElements,
                                                       replacements: util.List[Pair[TextRange, String]],
                                                       lastElement: PsiElement): Unit =
    data.getList match {
      case tupleType: ScTupleTypeElement =>
        super.addTailReplacementsForJoining(data, replacements, tupleType.typeList)
      case _ =>
    }
}

final class ScalaSplitJoinTuplesContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "tuple elements"

  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent(tuple: ScTuple) =>
        new ListWithElements(tuple, tuple.exprs.asJava)
      case _ => null
    }
}


final class ScalaSplitJoinTypeArgumentsContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "type arguments"

  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent(list: ScTypeArgs) =>
        new ListWithElements(list, list.typeArgs.asJava)
      case _ => null
    }
}

final class ScalaSplitJoinTypeParametersContext extends AbstractScalaSplitJoinContext {
  override val kind: String = "type parameters"

  override def extractData(element: PsiElement): ListWithElements =
    element match {
      case Parent(clause: ScTypeParamClause) =>
        new ListWithElements(clause, clause.typeParameters.asJava)
      case _ => null
    }
}
