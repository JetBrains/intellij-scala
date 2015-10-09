package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * Nikolay.Tropin
 * 2014-09-28
 */
object ScalaEvaluatorBuilder extends EvaluatorBuilder {
  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    if (codeFragment.getLanguage.isInstanceOf[JavaLanguage])
      return EvaluatorBuilderImpl.getInstance().build(codeFragment, position) //java builder (e.g. SCL-6117)

    val scalaFragment = codeFragment match {
      case sf: ScalaCodeFragment => sf
      case _ => throw EvaluationException(ScalaBundle.message("non-scala.code.fragment"))
    }

    val project = codeFragment.getProject

    val cache = ScalaEvaluatorCache.getInstance(project)
    val cached: Option[ExpressionEvaluator] = {
      try cache.get(position, codeFragment)
      catch {
        case e: Exception =>
          cache.clear()
          None
      }
    }

    def buildSimpleEvaluator = {
      cached.getOrElse {
        val newEvaluator = new ScalaEvaluatorBuilder(scalaFragment, position).getEvaluator
        val unwrapped = new UnwrapRefEvaluator(newEvaluator)
        cache.add(position, scalaFragment, new ExpressionEvaluatorImpl(unwrapped))
      }
    }

    def buildCompilingEvaluator: ExpressionEvaluator = {
      val compilingEvaluator = new ScalaCompilingEvaluator(position.getElementAt, scalaFragment)
      cache.add(position, scalaFragment, compilingEvaluator)
    }

    try buildSimpleEvaluator
    catch {
      case e: NeedCompilationException =>
        buildCompilingEvaluator
      case e: EvaluateException => throw e
    }
  }
}

private[evaluation] class NeedCompilationException(message: String) extends EvaluateException(message)

private[evaluation] class ScalaEvaluatorBuilder(codeFragment: ScalaCodeFragment, val position: SourcePosition)
        extends ScalaEvaluatorBuilderUtil {

  import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil._

  val contextClass =  {
    if (position == null) null
    else getContextClass(position.getElementAt, strict = false)
  }

  def getEvaluator: Evaluator = scalaCodeFragmentEvaluator(codeFragment)

  protected def evaluatorFor(element: PsiElement): Evaluator = {
    element match {
      case implicitlyConvertedTo(expr) => evaluatorFor(expr)
      case needsCompilation(message) => throw new NeedCompilationException(message)
      case expr: ScExpression =>
        val innerEval = expr match {
          case lit: ScLiteral => literalEvaluator(lit)
          case mc: ScMethodCall => scMethodCallEvaluator(mc)
          case ref: ScReferenceExpression => refExpressionEvaluator(ref)
          case t: ScThisReference => thisOrSuperEvaluator(t.reference, isSuper = false)
          case t: ScSuperReference => thisOrSuperEvaluator(t.reference, isSuper = true)
          case tuple: ScTuple => tupleEvaluator(tuple)
          case newTd: ScNewTemplateDefinition => newTemplateDefinitionEvaluator(newTd)
          case inf: ScInfixExpr => infixExpressionEvaluator(inf)
          case ScParenthesisedExpr(inner) => evaluatorFor(inner)
          case p: ScPrefixExpr => prefixExprEvaluator(p)
          case p: ScPostfixExpr => postfixExprEvaluator(p)
          case stmt: ScIfStmt => ifStmtEvaluator(stmt)
          case ws: ScWhileStmt => whileStmtEvaluator(ws)
          case doSt: ScDoStmt => doStmtEvaluator(doSt)
          case block: ScBlock => blockExprEvaluator(block)
          case call: ScGenericCall => methodCallEvaluator(call, Nil, Map.empty)
          case stmt: ScAssignStmt => assignmentEvaluator(stmt)
          case stmt: ScTypedStmt => evaluatorFor(stmt.expr)
          case e => throw EvaluationException(s"This type of expression is not supported: ${e.getText}")
        }
        postProcessExpressionEvaluator(expr, innerEval)
      case fragment: ScalaCodeFragment => scalaCodeFragmentEvaluator(fragment)
      case e => throw EvaluationException(s"This type of element is not supported: ${e.getText}")
    }
  }

  def scalaCodeFragmentEvaluator(fragment: ScalaCodeFragment): Evaluator = {
    val fragmentEvaluator = new CodeFragmentEvaluator(null)
    val childrenEvaluators = fragment.children.collect {
      case e @ (_: ScBlockStatement | _: ScMember) => evaluatorFor(e)
    }
    fragmentEvaluator.setStatements(childrenEvaluators.toArray)
    fragmentEvaluator
  }
}

private object needsCompilation {
  def message(kind: String) = Some(s"Evaluation of $kind needs compilation")

  def unapply(elem: PsiElement): Option[String] = elem match {
    case expr if ScUnderScoreSectionUtil.isUnderscoreFunction(expr) => message("anonymous function")
    case funExpr: ScFunctionExpr =>
      message("anonymous function")
    case forSt: ScForStatement =>
      message("for expression")
    case tryStmt: ScTryStmt =>
      message("try statement")
    case ret: ScReturnStmt =>
      message("return statement")
    case ms: ScMatchStmt =>
      message("match statement")
    case throwStmt: ScThrowStmt =>
      message("throw statement")
    case v @ (_: ScVariable | _: ScValue) => message("variable definition")
    case t: ScTypeAlias => message("type alias")
    case newTd: ScNewTemplateDefinition if DebuggerUtil.generatesAnonClass(newTd) =>
      message("anonymous class")
    case _ => None
  }
}