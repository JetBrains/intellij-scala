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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

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
    val newPattern = ScalaPsiElementFactory.createPatternFromText(pattern.name, pattern.getManager)
    pattern.replace(newPattern)
  }

  def parameterWithoutType(param: ScParameter) {
    import param.typeSystem
    param.parentsInFile.findByType(classOf[ScFunctionExpr]) match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType() match {
          case Some(FunctionType(_, params)) =>
            if (index >= 0 && index < params.length) {
              val paramExpectedType = params(index)
              val param1 = param.getParent match {
                case x: ScParameterClause if x.parameters.length == 1 =>
                  // ensure  that the parameter is wrapped in parentheses before we add the type annotation.
                  val clause: PsiElement = x.replace(ScalaPsiElementFactory.createClauseForFunctionExprFromText("(" + param.getText + ")", param.getManager))
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
    val newParam = ScalaPsiElementFactory.createParameterFromText(param.name, param.getManager)
    val newClause = ScalaPsiElementFactory.createClauseForFunctionExprFromText(newParam.getText, param.getManager)
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

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement)
                       (implicit typeSystem: TypeSystem = context.typeSystem) {
    def addActualType(annotation: ScTypeElement) = {
      val parent = anchor.getParent
      val added = parent.addAfter(annotation, anchor)
      val colon = ScalaPsiElementFactory.createColon(context.getManager)
      val whitespace = ScalaPsiElementFactory.createWhitespace(context.getManager)
      parent.addAfter(whitespace, anchor)
      parent.addAfter(colon, anchor)
      added
    }

    val tps = UpdateStrategy.annotationsFor(t, context)

    val added = addActualType(tps.head)
    editor match {
      case Some(e) if tps.size > 1 =>
        val texts = tps.flatMap(_.getType().toOption).map(ScTypeText)
        val expr = new ChooseTypeTextExpression(texts)
        IntentionUtil.startTemplate(added, context, expr, e)
      case _ => ScalaPsiUtil.adjustTypes(added)
    }
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

  def annotationsFor(t: ScType, context: PsiElement)
                    (implicit typeSystem: TypeSystem = context.typeSystem): Seq[ScTypeElement] = {
    def typeElemfromText(s: String) = ScalaPsiElementFactory.createTypeElementFromText(s, context.getManager)
    def typeElemFromType(tp: ScType) = typeElemfromText(tp.canonicalText)

    t match {
      case ScCompoundType(comps, _, _) =>
        val uselessTypes = Set("_root_.scala.Product", "_root_.scala.Serializable", "_root_.java.lang.Object")
        comps.map(_.canonicalText).filterNot(uselessTypes.contains) match {
          case Seq(base) => Seq(typeElemfromText(base))
          case types => (Seq(types.mkString(" with ")) ++ types).flatMap { t =>
            Seq(typeElemfromText(t))
          }
        }
      case someOrNone if Set("_root_.scala.Some", "_root_.scala.None").exists(someOrNone.canonicalText.startsWith) =>
        val replacement = BaseTypes.get(someOrNone).find(_.canonicalText.startsWith("_root_.scala.Option")).getOrElse(someOrNone)
        Seq(typeElemFromType(replacement))
      case tp =>
        val project = context.getProject
        tp.extractClass(project) match {
          case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(isSealed) =>
            val sealedType = BaseTypes.get(tp).find(_.extractClass(project).exists(isSealed))
            (sealedType.toSeq :+ tp).map(typeElemFromType)
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName.startsWith("scala.collection") =>
            val goodTypes = Set(
              "_root_.scala.collection.Seq[",
              "_root_.scala.collection.mutable.Seq[",
              "_root_.scala.collection.immutable.Seq[",
              "_root_.scala.collection.Set[",
              "_root_.scala.collection.mutable.Set[",
              "_root_.scala.collection.immutable.Set[",
              "_root_.scala.collection.Map[",
              "_root_.scala.collection.mutable.Map[",
              "_root_.scala.collection.immutable.Map["
            )
            val baseTypes = BaseTypes.get(tp).map(_.canonicalText).filter(t => goodTypes.exists(t.startsWith))
            (tp.canonicalText +: baseTypes).map(typeElemfromText)
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
