package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createClauseForFunctionExprFromText, createColon, createTypeElementFromText, createWhitespace}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScCompoundType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

class AddOnlyStrategy(editor: Option[Editor] = None) extends Strategy {

  def functionWithType(function: ScFunctionDefinition,
                       typeElement: ScTypeElement): Unit = {}

  def valueWithType(value: ScPatternDefinition,
                    typeElement: ScTypeElement): Unit = {}

  def variableWithType(variable: ScVariableDefinition,
                       typeElement: ScTypeElement): Unit = {}

  override def functionWithoutType(function: ScFunctionDefinition): Unit =
    function.returnType.foreach {
      addTypeAnnotation(_, function, function.paramClauses)
    }

  override def valueWithoutType(value: ScPatternDefinition): Unit =
    value.getType().foreach {
      addTypeAnnotation(_, value, value.pList)
    }

  override def variableWithoutType(variable: ScVariableDefinition): Unit =
    variable.getType().foreach {
      addTypeAnnotation(_, variable, variable.pList)
    }

  override def patternWithoutType(pattern: ScBindingPattern): Unit =
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

  override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Unit =
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

  override def parameterWithoutType(param: ScParameter): Unit =
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

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement) {
    import AddOnlyStrategy._
    val tps = annotationsFor(t, context)
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
}

object AddOnlyStrategy {

  def addActualType(annotation: ScTypeElement, anchor: PsiElement): PsiElement = {
    implicit val ctx: ProjectContext = anchor

    val parent = anchor.getParent
    val added = parent.addAfter(annotation, anchor)

    parent.addAfter(createWhitespace, anchor)
    parent.addAfter(createColon, anchor)

    added
  }

  def annotationFor(`type`: ScType, anchor: PsiElement): Option[ScTypeElement] =
    annotationsFor(`type`, anchor).headOption

  private def annotationsFor(`type`: ScType, context: PsiElement): Seq[ScTypeElement] = {
    import `type`.projectContext

    val canonicalTypes = `type` match {
      case compound@ScCompoundType(comps, _, _) =>
        val uselessTypes = Set("_root_.scala.Product", "_root_.scala.Serializable", "_root_.java.lang.Object")
        val filtered = comps.filterNot(c => uselessTypes.contains(c.canonicalText))
        val newCompType = compound.copy(components = filtered)
        Seq(newCompType.canonicalText)
      case tp =>
        import BaseTypes.get

        val baseTypes = tp.extractClass match {
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName == "scala.Some" =>
            get(tp).map(_.canonicalText)
              .filter(_.startsWith("_root_.scala.Option"))
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName.startsWith("scala.collection") =>
            val goodTypes = Set(
              "_root_.scala.collection.mutable.Seq[",
              "_root_.scala.collection.immutable.Seq[",
              "_root_.scala.collection.mutable.Set[",
              "_root_.scala.collection.immutable.Set[",
              "_root_.scala.collection.mutable.Map[",
              "_root_.scala.collection.immutable.Map["
            )

            get(tp).map(_.canonicalText)
              .filter(t => goodTypes.exists(t.startsWith))
          case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(isSealed) =>
            get(tp).find(_.extractClass.exists(isSealed)).toSeq
              .map(_.canonicalText)
          case _ => Seq.empty
        }

        tp.canonicalText +: baseTypes
    }

    canonicalTypes.map(createTypeElementFromText)
  }

  private[this] def isSealed(clazz: PsiClass) = clazz match {
    case _: ScClass | _: ScTrait => clazz.hasModifierPropertyScala("sealed")
    case _ => false
  }
}
