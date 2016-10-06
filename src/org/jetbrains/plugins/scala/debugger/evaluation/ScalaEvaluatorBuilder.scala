package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.{LazyVal, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

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
    val cached: Option[Evaluator] = {
      try cache.get(position, codeFragment)
      catch {
        case _: Exception =>
          cache.clear()
          None
      }
    }

    def buildSimpleEvaluator = {
      cached.getOrElse {
        val newEvaluator = new ScalaEvaluatorBuilder(scalaFragment, position).getEvaluator
        cache.add(position, scalaFragment, newEvaluator)
      }
    }

    def buildCompilingEvaluator: ScalaCompilingEvaluator = {
      val compilingEvaluator = new ScalaCompilingEvaluator(position.getElementAt, scalaFragment)
      cache.add(position, scalaFragment, compilingEvaluator).asInstanceOf[ScalaCompilingEvaluator]
    }

    try {
      new ExpressionEvaluatorImpl(buildSimpleEvaluator)
    }
    catch {
      case _: NeedCompilationException =>
        new ScalaCompilingExpressionEvaluator(buildCompilingEvaluator)
      case e: EvaluateException => throw e
    }
  }
}

private[evaluation] class NeedCompilationException(message: String) extends EvaluateException(message)

private[evaluation] class ScalaEvaluatorBuilder(val codeFragment: ScalaCodeFragment, val position: SourcePosition)
        extends ScalaEvaluatorBuilderUtil with SyntheticVariablesHelper {

  import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil._

  val contextClass: PsiElement =
    Option(position).map(pos => getContextClass(pos.getElementAt, strict = false)).orNull

  def getEvaluator: Evaluator = new UnwrapRefEvaluator(fragmentEvaluator(codeFragment))

  protected def evaluatorFor(element: PsiElement): Evaluator = {
    implicit val typeSystem = element.typeSystem
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
      case pd: ScPatternDefinition => patternDefinitionEvaluator(pd)
      case vd: ScVariableDefinition => variableDefinitionEvaluator(vd)
      case e => throw EvaluationException(s"This type of element is not supported: ${e.getText}")
    }
  }

  def fragmentEvaluator(fragment: ScalaCodeFragment): Evaluator = {
    val childrenEvaluators = fragment.children.filter(!_.isInstanceOf[ScImportStmt]).collect {
      case e @ (_: ScBlockStatement | _: ScMember) => evaluatorFor(e)
    }
    new BlockStatementEvaluator(childrenEvaluators.toArray)
  }
}

private[evaluation] trait SyntheticVariablesHelper {
  private var currentHolder = new SyntheticVariablesHolderEvaluator(null)

  protected def withNewSyntheticVariablesHolder(evaluatorComputation: => Evaluator): Evaluator = {
    val old = currentHolder
    val newEvaluator = new SyntheticVariablesHolderEvaluator(currentHolder)
    currentHolder = newEvaluator
    var result: Evaluator = null
    try {
      result = evaluatorComputation
    }
    finally {
      currentHolder = old
    }
    result
  }

  protected def createSyntheticVariable(name: String) = currentHolder.setInitialValue(name, null)
  protected def syntheticVariableEvaluator(name: String) = new SyntheticVariableEvaluator(currentHolder, name)
}

private object needsCompilation {
  def message(kind: String) = Some(s"Evaluation of $kind needs compilation")

  def unapply(elem: PsiElement): Option[String] = elem match {
    case m: ScMember => m match {
      case td: ScTemplateDefinition =>
        td match {
          case _: ScObject => message("object")
          case _: ScClass => message("class")
          case _: ScTrait => message("trait")
          case newTd: ScNewTemplateDefinition if DebuggerUtil.generatesAnonClass(newTd) =>
            message("anonymous class")
          case _ => None
        }
      case _: ScTypeAlias => message("type alias")
      case _: ScFunction => message("function definition")
      case (_: ScVariableDeclaration | _: ScValueDeclaration) => message("variable declaration")
      case LazyVal(_) => message("lazy val definition")
      case _ => None
    }
    case expr if ScalaEvaluatorBuilderUtil.isGenerateAnonfun(expr) => message("anonymous function")
    case _: ScForStatement => message("for expression")
    case _: ScTryStmt => message("try statement")
    case _: ScReturnStmt => message("return statement")
    case _: ScMatchStmt => message("match statement")
    case _: ScThrowStmt => message("throw statement")
    case _: ScXmlExpr => message("xml expression")
    case interpolated: ScInterpolatedStringLiteral if interpolated.getType != InterpolatedStringType.STANDART =>
      message("interpolated string")
    case _ => None
  }
}
