package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.debugger.evaluation.newevaluator._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

private[debugger] object ExpressionEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val element = codeFragment.asInstanceOf[ScalaCodeFragment].children.toList.head
    new ExpressionEvaluatorImpl(buildEvaluator(element, position))
  }

  private def buildEvaluator(element: PsiElement, position: SourcePosition): Evaluator =
    element match {
      case ScParenthesisedExpr(expr) => buildEvaluator(expr, position)

      case lit: ScLiteral =>
        val tpe = lit.`type`().getOrAny match {
          case literalType: ScLiteralType => literalType.wideType
          case t => t
        }

        new LiteralEvaluator(lit.getValue, literalExpectedType(tpe))

      case fun: ScFunctionExpr => buildFunctionExpressionEvaluator(fun, position)
      case expr: ScReferenceExpression => buildReferenceExpressionEvaluator(expr, position)
      case _: ScThisReference => new ThisEvaluator()
      case _ => throw EvaluationException(s"Cannot evaluate expression ${element.getText}")
    }

  private def buildReferenceExpressionEvaluator(expression: ScReferenceExpression, position: SourcePosition): Evaluator =
    expression.resolve() match {
      case rp: ScReferencePattern if rp.isClassMember =>
        val isPrivate =
          rp.getModifierList match {
            case sml: ScModifierList => sml.isPrivate
            case _ => false
          }

        val containingClass = rp.containingClass
        val suffix = if (containingClass.is[ScObject]) "$" else ""
        val thisEval = new StackWalkingThisEvaluator(s"${containingClass.qualifiedName}$suffix")
        if (isPrivate) {
          val fieldClass = rp.`type`().getOrAny.extractClass.get
          new FieldEvaluator(thisEval, FieldEvaluator.createClassFilter(fieldClass), rp.name)
        } else {
          new MethodEvaluator(thisEval, null, rp.name, null, Array.empty)
        }
      case rp: ScReferencePattern =>
        val name = rp.name
        val sourceName = position.getFile.getVirtualFile.getName
        if (rp.isVar) {
          new LocalVarEvaluator(name, sourceName)
        } else {
          new LocalValEvaluator(name, sourceName)
        }

      case p: ScParameter =>
        new LocalValEvaluator(p.name, position.getFile.getVirtualFile.getName)

      case TypeMatchCase(expr) => buildEvaluator(expr, position)

      case _ =>
        throw EvaluationException(s"Cannot evaluate reference expression ${expression.getText}")
    }

  private object TypeMatchCase {
    def unapply(element: PsiElement): Option[ScExpression] = {
      Option(element)
        .collect { case tp: ScTypedPattern => tp }
        .flatMap(_.parentOfType(Seq(classOf[ScMatch])))
        .collect { case m: ScMatch => m }
        .flatMap(_.expression)
    }
  }

  private def literalExpectedType(tpe: ScType): String = {
    val stdTypes = tpe.projectContext.stdTypes
    import stdTypes._

    tpe match {
      case Boolean => "boolean"
      case Byte => "byte"
      case Char => "char"
      case Double => "double"
      case Float => "float"
      case Int => "int"
      case Long => "long"
      case Short => "short"
      case _ => ""
    }
  }

  private def buildFunctionExpressionEvaluator(fun: ScFunctionExpr, position: SourcePosition): Evaluator = {
    val tpe = fun.`type`().getOrAny.canonicalText
    val params = fun.parameters.map(p => (p.name, p.`type`().getOrAny.canonicalText))
    val body = fun.result.map(_.getText).getOrElse("{}")
    new LambdaExpressionEvaluator(tpe, params, body, position.getElementAt)
  }
}
