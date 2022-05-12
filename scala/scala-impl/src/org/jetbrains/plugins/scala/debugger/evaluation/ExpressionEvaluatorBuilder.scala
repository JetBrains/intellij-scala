package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression.{FieldEvaluator => _, _}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.reflect.NameTransformer

private[evaluation] object ExpressionEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val element = codeFragment.asInstanceOf[ScalaCodeFragment].children.toList.head
    new ExpressionEvaluatorImpl(buildEvaluator(element, position))
  }

  private def buildEvaluator(element: PsiElement, position: SourcePosition): Evaluator = element match {
    case fun: ScFunctionExpr => LambdaExpressionEvaluator.fromFunctionExpression(fun, position.getElementAt)
    case lit: ScLiteral => LiteralEvaluator.fromLiteral(lit)
    case ref: ScThisReference =>
      ref.refTemplate.collect {
        case o: ScObject => s"${o.getQualifiedNameForDebugger}$$"
        case c: ScClass => c.getQualifiedNameForDebugger
        case t: ScTrait => t.getQualifiedNameForDebugger
      }.map(JVMNameUtil.getJVMRawText).fold[Evaluator](new ThisEvaluator())(name => new StackWalkingThisEvaluator(name, None))
    case call: ScMethodCall =>
      val params = call.matchedParameters.map(_._1).map(buildEvaluator(_, position))
      val resolved = call.getInvokedExpr.asInstanceOf[ScReferenceExpression].resolve().asInstanceOf[ScFunctionDefinition]
      val name = resolved.name
      new MethodEvaluator(new ThisEvaluator(), null, name, null, params.toArray)
    case ref: ScReferenceExpression =>
      ref.resolve() match {
        case FunctionParameter(name, _, scope) => new LocalVariableEvaluator(name, scope)
        case LocalVariable(name, _, scope) => new LocalVariableEvaluator(name, scope)
        case ClassMemberVariable(name, tpe, _, jvmName, typeFilter) =>
          val instance = new StackWalkingThisEvaluator(jvmName, Some(typeFilter))
          typeFilter match {
            case StackWalkingThisEvaluator.TypeFilter.ContainsField(_) =>
              new FieldEvaluator(instance, name, DebuggerUtil.getJVMQualifiedName(tpe))
            case StackWalkingThisEvaluator.TypeFilter.ContainsMethod(_) =>
              new MethodEvaluator(instance, null, name, null, Array.empty)
          }
      }
  }

  private[evaluation] object LocalVariable {
    def unapply(element: PsiElement): Option[(String, ScType, String)] =
      Option(element)
        .collect { case rp: ScReferencePattern if !rp.isClassMember => rp }
        .flatMap { rp =>
          rp.parentOfType[ScBlockExpr].flatMap {
            case _: ScConstrBlockExpr => Some("<init>")
            case blk if blk.isPartialFunction => Some("applyOrElse")
            case blk => extractScopeName(blk)
          }.map(name => (rp.name, rp.`type`().getOrAny, name))
        }

    private def extractScopeName(element: PsiElement): Option[String] =
      element.parentOfType(Seq(classOf[ScFunctionDefinition], classOf[ScValueOrVariableDefinition], classOf[ScFunctionExpr], classOf[ScTemplateBody])).flatMap {
        case fun: ScFunctionDefinition => Some(NameTransformer.encode(fun.name))
        case valDef: ScValueOrVariableDefinition => extractScopeName(valDef)
        case _: ScFunctionExpr => Some("anonfun")
        case _: ScTemplateBody => Some("init>")
      }
  }

  private[evaluation] object ClassMemberVariable {
    def unapply(element: PsiElement): Option[(String, ScType, PsiClass, JVMName, StackWalkingThisEvaluator.TypeFilter)] =
      Option(element)
        .collect {
          case rp: ScReferencePattern if rp.isClassMember =>
            val name = rp.name

            val containingClass = rp.containingClass match {
              case td: ScNewTemplateDefinition => td.supers.head
              case c => c
            }

            val jvmName = containingClass match {
              case o: ScObject => JVMNameUtil.getJVMRawText(s"${o.getQualifiedNameForDebugger}$$")
              case c: ScClass => JVMNameUtil.getJVMRawText(c.getQualifiedNameForDebugger)
              case t: ScTrait => JVMNameUtil.getJVMRawText(t.getQualifiedNameForDebugger)
              case c => JVMNameUtil.getJVMQualifiedName(c)
            }

            val typeFilter = rp.getModifierList match {
              case ml: ScModifierList if !ml.isPrivate => StackWalkingThisEvaluator.TypeFilter.ContainsMethod(name)
              case _ => StackWalkingThisEvaluator.TypeFilter.ContainsField(name)
            }

            (name, rp.`type`().getOrAny, containingClass, jvmName, typeFilter)
        }
  }

  private[evaluation] object FunctionParameter {
    def unapply(element: PsiElement): Option[(String, ScType, String)] =
      Option(element)
        .collect { case p: ScParameter => p }
        .flatMap { p =>
          p.parentOfType(Seq(classOf[ScFunctionDefinition], classOf[ScFunctionExpr])).flatMap {
            case fun: ScFunctionDefinition => Some(NameTransformer.encode(fun.name))
            case _: ScFunctionExpr => Some("anonfun")
          }.map(name => (p.name, p.`type`().getOrAny, name))
        }
  }
}
