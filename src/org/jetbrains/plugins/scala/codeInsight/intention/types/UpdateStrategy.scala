package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

/**
 * Pavel.Fatin, 28.04.2010
 */

class AddOrRemoveStrategy(editor: Option[Editor]) extends UpdateStrategy(editor)

class RegenerateStrategy(editor: Option[Editor]) extends StrategyAdapter {
  private val strategy = new AddOrRemoveStrategy(editor)

  override def functionWithType(function: ScFunctionDefinition): Unit = {
    strategy.functionWithType(function)
    strategy.functionWithoutType(function)
  }

  override def variableWithType(variable: ScVariableDefinition): Unit = {
    strategy.variableWithType(variable)
    strategy.variableWithoutType(variable)
  }

  override def valueWithType(value: ScPatternDefinition): Unit = {
    strategy.valueWithType(value)
    strategy.valueWithoutType(value)
  }
}

object AddOrRemoveStrategy {
  def withoutEditor = new AddOrRemoveStrategy(None)
}

class AddOnlyStrategy(editor: Option[Editor]) extends UpdateStrategy(editor) {
  override def functionWithType(function: ScFunctionDefinition): Unit = {}
  override def parameterWithType(param: ScParameter): Unit = {}
  override def patternWithType(pattern: ScTypedPattern): Unit = {}
  override def valueWithType(value: ScPatternDefinition): Unit = {}
  override def variableWithType(variable: ScVariableDefinition): Unit = {}
}

object AddOnlyStrategy {
  def withoutEditor = new AddOrRemoveStrategy(None)
}

abstract class UpdateStrategy(editor: Option[Editor]) extends Strategy {
  def functionWithoutType(function: ScFunctionDefinition) {
    function.returnType.foreach {
      addTypeAnnotation(_, function, function.paramClauses)
    }
  }

  def functionWithType(function: ScFunctionDefinition) {
    function.returnTypeElement.foreach(removeTypeAnnotation)
  }

  def valueWithoutType(value: ScPatternDefinition) {
    value.getType(TypingContext.empty).toOption.foreach {
      addTypeAnnotation(_, value, value.pList)
    }
  }

  def valueWithType(value: ScPatternDefinition) {
    value.typeElement.foreach(removeTypeAnnotation)
  }

  def variableWithoutType(variable: ScVariableDefinition) {
    variable.getType(TypingContext.empty).toOption.foreach {
      addTypeAnnotation(_, variable, variable.pList)
    }
  }

  def variableWithType(variable: ScVariableDefinition) {
    variable.typeElement.foreach(removeTypeAnnotation)
  }

  def patternWithoutType(pattern: ScBindingPattern) {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }
  }

  def wildcardPatternWithoutType(pattern: ScWildcardPattern) {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }
  }

  def patternWithType(pattern: ScTypedPattern) {
    val newPattern = createPatternFromText(pattern.name)(pattern.getManager)
    pattern.replace(newPattern)
  }

  def parameterWithoutType(param: ScParameter) {
    param.parentsInFile.findByType[ScFunctionExpr] match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType() match {
          case Some(FunctionType(_, params)) =>
            if (index >= 0 && index < params.length) {
              val paramExpectedType = params(index)
              val param1 = param.getParent match {
                case x: ScParameterClause if x.parameters.length == 1 =>
                  // ensure  that the parameter is wrapped in parentheses before we add the type annotation.
                  val clause: PsiElement = x.replace(createClauseForFunctionExprFromText(param.getText.parenthesize(true))(param.getManager))
                  clause.asInstanceOf[ScParameterClause].parameters.head
                case _ => param
              }
              addTypeAnnotation(paramExpectedType, param1.getParent, param1)
            }
          case _ =>
        }
      case _ =>
    }
  }

  def parameterWithType(param: ScParameter) {
    import param.projectContext

    val newParam = createParameterFromText(param.name)
    val newClause = createClauseForFunctionExprFromText(newParam.getText)
    val expr : ScFunctionExpr = PsiTreeUtil.getParentOfType(param, classOf[ScFunctionExpr], false)
    if (expr != null) {
      val firstClause = expr.params.clauses.head
      val fcText = firstClause.getText
      if (expr.parameters.size == 1 && fcText.startsWith("(") && fcText.endsWith(")"))
        firstClause.replace(newClause)
      else param.replace(newParam)
    }
    else param.replace(newParam)
  }

  def addActualType(annotation: ScTypeElement, anchor: PsiElement): PsiElement = {
    implicit val ctx: ProjectContext = anchor

    val parent = anchor.getParent
    val added = parent.addAfter(annotation, anchor)

    parent.addAfter(createWhitespace, anchor)
    parent.addAfter(createColon, anchor)
    added
  }

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement) {

    val tps = UpdateStrategy.annotationsFor(t, context)

    val added = addActualType(tps.head, anchor)
    editor match {
      case Some(e) if tps.size > 1 =>
        val texts = tps.reverse.flatMap(_.getType().toOption).map(ScTypeText)
        val expr = new ChooseTypeTextExpression(texts)
        // TODO Invoke the simplification
        IntentionUtil.startTemplate(added, context, expr, e)
      case _ =>
        ScalaPsiUtil.adjustTypes(added)
        rightExpressionOf(context).foreach(simplify)
    }
  }

  private def rightExpressionOf(definition: PsiElement): Option[ScExpression] = definition match {
    case variable: ScVariableDefinition => variable.expr
    case pattern: ScPatternDefinition => pattern.expr
    case function: ScFunctionDefinition => function.body
    case _ => None
  }

  private def simplify(expression: ScExpression): Unit = expression match {
    case call: ScGenericCall if TypeAnnotationUtil.isEmptyCollectionFactory(call) =>
      val s = call.getText
      implicit val manager = expression.projectContext
      val newExpression = ScalaPsiElementFactory.createExpressionFromText(s.substring(0, s.indexOf('[')))
      expression.replace(newExpression)
    case _ =>
  }

  def removeTypeAnnotation(e: PsiElement) {
    e.prevSiblings.find(_.getText == ":").foreach(_.delete())
    e.delete()
  }
}

object UpdateStrategy {
  private def isSealed(c: PsiClass) = c match {
    case _: ScClass | _: ScTrait => c.hasModifierPropertyScala("sealed")
    case _ => false
  }

  def annotationsFor(t: ScType, context: PsiElement): Seq[ScTypeElement] = {
    import t.projectContext

    def typeElemfromText(s: String) = createTypeElementFromText(s)(context.getManager)
    def typeElemFromType(tp: ScType) = typeElemfromText(tp.canonicalText)

    t match {
      case compound @ ScCompoundType(comps, _, _) =>
        val uselessTypes = Set("_root_.scala.Product", "_root_.scala.Serializable", "_root_.java.lang.Object")
        val filtered = comps.filterNot(c => uselessTypes.contains(c.canonicalText))
        val newCompType = compound.copy(components = filtered)
        Seq(typeElemfromText(newCompType.canonicalText))
      case tp =>
        val project = context.getProject
        tp.extractClass match {
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName == "scala.Some" =>
            val baseTypes = BaseTypes.get(tp).map(_.canonicalText).filter(_.startsWith("_root_.scala.Option"))
            (tp.canonicalText +: baseTypes).map(typeElemfromText)
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName.startsWith("scala.collection") =>
            val goodTypes = Set(
              "_root_.scala.collection.mutable.Seq[",
              "_root_.scala.collection.immutable.Seq[",
              "_root_.scala.collection.mutable.Set[",
              "_root_.scala.collection.immutable.Set[",
              "_root_.scala.collection.mutable.Map[",
              "_root_.scala.collection.immutable.Map["
            )
            val baseTypes = BaseTypes.get(tp).map(_.canonicalText).filter(t => goodTypes.exists(t.startsWith))
            (tp.canonicalText +: baseTypes).map(typeElemfromText)
          case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(isSealed) =>
            val sealedType = BaseTypes.get(tp).find(_.extractClass.exists(isSealed))
            (tp +: sealedType.toSeq).map(typeElemFromType)
          case _ => Seq(typeElemFromType(tp))
        }
    }
  }
}

class StrategyAdapter extends Strategy {
  override def functionWithoutType(function: ScFunctionDefinition): Unit = ()

  override def patternWithType(pattern: ScTypedPattern): Unit = ()

  override def variableWithoutType(variable: ScVariableDefinition): Unit = ()

  override def parameterWithType(param: ScParameter): Unit = ()

  override def parameterWithoutType(param: ScParameter): Unit = ()

  override def variableWithType(variable: ScVariableDefinition): Unit = ()

  override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Unit = ()

  override def functionWithType(function: ScFunctionDefinition): Unit = ()

  override def valueWithoutType(value: ScPatternDefinition): Unit = ()

  override def valueWithType(value: ScPatternDefinition): Unit = ()

  override def patternWithoutType(pattern: ScBindingPattern): Unit = ()
}
