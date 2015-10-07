package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.annotation.tailrec
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
        val newEvaluator = ScalaEvaluator(scalaFragment)(position)
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

private[evaluation] class EvaluatorBuilderVisitor(element: PsiElement, _contextClass: Option[PsiClass] = None)
                                                 (implicit val position: SourcePosition)
        extends ScalaElementVisitor with ScalaEvaluatorBuilderUtil {

  import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil._

  val contextClass = _contextClass.getOrElse {
    if (position == null) null
    else getContextClass(position.getElementAt, strict = false)
  }

  private var myResult: Evaluator = null

  def getEvaluator: Evaluator = buildNew()

  private def buildNew(): Evaluator = {
    try {
      element match {
        case expr if ScUnderScoreSectionUtil.isUnderscoreFunction(expr) =>
          throw new NeedCompilationException("Anonymous functions are not supported")
        case implicitlyConvertedTo(expr) => expr.accept(this)
        case elem => elem.accept(this)
      }
    } catch {
      case e: EvaluateRuntimeException => throw e.getCause
    }

    myResult
  }

  override def visitFile(file: PsiFile) {
    if (!file.isInstanceOf[ScalaCodeFragment]) return
    val fragmentEvaluator = new CodeFragmentEvaluator(null)
    val childrenEvaluators = file.children.map(ScalaEvaluator(_)).filter(_ != null)
    fragmentEvaluator.setStatements(childrenEvaluators.toArray)
    myResult = fragmentEvaluator
    super.visitFile(file)
  }

  override def visitFunctionExpression(stmt: ScFunctionExpr) {
    throw new NeedCompilationException("Anonymous functions are not supported")
  }

  override def visitExprInParent(expr: ScParenthesisedExpr) {
    expr.expr match {
      case Some(ex) => myResult = ScalaEvaluator(ex)
      case None => throw EvaluationException(ScalaBundle.message("invalid.expression.in.parentheses", expr.getText))
    }
    super.visitExprInParent(expr)
  }

  override def visitPrefixExpression(p: ScPrefixExpr) {
    val newExprText = s"(${p.operand.getText}).unary_${p.operation.refName}"
    val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, p.getContext, p)
    myResult = ScalaEvaluator(newExpr)
    super.visitPrefixExpression(p)
  }

  override def visitPostfixExpression(p: ScPostfixExpr) {
    val equivRef = ScalaPsiElementFactory.createEquivQualifiedReference(p)
    myResult = ScalaEvaluator(equivRef)
    super.visitPostfixExpression(p)
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) {
    ref.qualifier match {
      case Some(implicitlyConvertedTo(expr)) =>
        val copy = ref.copy().asInstanceOf[ScReferenceExpression]
        copy.qualifier.get.replaceExpression(expr, removeParenthesis = false)
        myResult = ScalaEvaluator(copy)
      case _ =>
        val resolve: PsiElement = ref.resolve()
        myResult = evaluatorForReferenceWithoutParameters(ref.qualifier, resolve, ref)
    }
    visitExpression(ref)
  }

  override def visitIfStatement(stmt: ScIfStmt) {
    val condEvaluator = stmt.condition match {
      case Some(cond) => ScalaEvaluator(cond)
      case None => throw EvaluationException(ScalaBundle.message("if.statement.without.condition"))
    }
    val ifBranch = stmt.thenBranch match {
      case Some(th) => ScalaEvaluator(th)
      case None => throw EvaluationException(ScalaBundle.message("if.statement.without.if.branch"))
    }
    val elseBranch = stmt.elseBranch.map(ScalaEvaluator(_))
    myResult = new ScalaIfEvaluator(condEvaluator, ifBranch, elseBranch)
    super.visitIfStatement(stmt)
  }

  override def visitLiteral(l: ScLiteral) {
    myResult = l match {
      case interpolated: ScInterpolatedStringLiteral if interpolated.getType == InterpolatedStringType.FORMAT =>
        throw EvaluationException(ScalaBundle.message("formatted.interpolator.not.supported"))
      case interpolated: ScInterpolatedStringLiteral =>
        val evaluatorOpt = interpolated.getStringContextExpression.map(ScalaEvaluator(_))
        evaluatorOpt.getOrElse(ScalaLiteralEvaluator(l))
      case _ if l.isSymbol =>
        val value = l.getValue.asInstanceOf[Symbol].name
        val expr = ScalaPsiElementFactory.createExpressionFromText(s"""Symbol("$value")""", l.getContext)
        ScalaEvaluator(expr)
      case _ => ScalaLiteralEvaluator(l)
    }
    super.visitLiteral(l)
  }

  override def visitWhileStatement(ws: ScWhileStmt) {
    val condEvaluator = ws.condition match {
      case Some(cond) => ScalaEvaluator(cond)
      case None => throw EvaluationException(ScalaBundle.message("while.statement.without.condition"))
    }
    val iterationEvaluator = ws.body match {
      case Some(body) => ScalaEvaluator(body)
      case None => throw EvaluationException(ScalaBundle.message("while.statement.without.body"))
    }

    myResult = new WhileStatementEvaluator(condEvaluator, iterationEvaluator, null)
    super.visitWhileStatement(ws)
  }

  override def visitDoStatement(stmt: ScDoStmt) {
    val condEvaluator = stmt.condition match {
      case Some(cond) => ScalaEvaluator(cond)
      case None =>
        throw EvaluationException(ScalaBundle.message("do.statement.without.condition"))
    }
    val iterationEvaluator = stmt.getExprBody match {
      case Some(body) => ScalaEvaluator(body)
      case None =>
        throw EvaluationException(ScalaBundle.message("do.statement.without.body"))
    }
    myResult = new ScalaDoStmtEvaluator(condEvaluator, iterationEvaluator)
    super.visitDoStatement(stmt)
  }


  override def visitForExpression(forStmt: ScForStatement) {
    new NeedCompilationException("Its better to compile for expression")
    super.visitForExpression(forStmt)
  }

  override def visitBlockExpression(block: ScBlockExpr) {
    val evaluators = block.statements.map(ScalaEvaluator(_))
    myResult = new ScalaBlockExpressionEvaluator(evaluators.toSeq)
    super.visitBlockExpression(block)
  }

  override def visitTryExpression(tryStmt: ScTryStmt) {
    throw new NeedCompilationException("Try expression is not supported")
  }

  override def visitReturnStatement(ret: ScReturnStmt) {
    throw new NeedCompilationException("Return statement is not supported")
  }

  override def visitFunction(fun: ScFunction) {
    throw new NeedCompilationException("Function definition is not supported")
  }

  override def visitThisReference(t: ScThisReference) {
    myResult = thisOrSuperEvaluator(t.reference, isSuper = false)
    super.visitThisReference(t)
  }

  override def visitSuperReference(t: ScSuperReference) {
    myResult = thisOrSuperEvaluator(t.reference, isSuper = true)
    super.visitSuperReference(t)
  }

  override def visitGenericCallExpression(call: ScGenericCall) {
    myResult = methodCallEvaluator(call, Nil, Map.empty)
    super.visitGenericCallExpression(call)
  }

  override def visitMatchStatement(ms: ScMatchStmt) {
    throw new NeedCompilationException("Match statement is not supported")
  }

  override def visitThrowExpression(throwStmt: ScThrowStmt) {
    throw new NeedCompilationException("Throw statement is not supported")
  }

  override def visitAssignmentStatement(stmt: ScAssignStmt) {
    myResult = assignmentEvaluator(stmt)
    super.visitAssignmentStatement(stmt)
  }

  override def visitTypedStmt(stmt: ScTypedStmt) {
    myResult = ScalaEvaluator(stmt.expr)
    super.visitTypedStmt(stmt)
  }

  override def visitMethodCallExpression(parentCall: ScMethodCall) {
    def applyCall(invokedText: String, argsText: String) = {
      val newExprText = s"($invokedText).apply$argsText"
      ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, parentCall.getContext, parentCall)
    }

    @tailrec
    def collectArgumentsAndBuildEvaluator(call: ScMethodCall,
                                          collected: Seq[ScExpression] = Seq.empty,
                                          tailString: String = "",
                                          matchedParameters: Map[Parameter, Seq[ScExpression]] = Map.empty) {
      if (call.isApplyOrUpdateCall) {
        if (!call.isUpdateCall) {
          val expr = applyCall(call.getInvokedExpr.getText, call.args.getText + tailString)
          myResult = ScalaEvaluator(expr)
          return
        } else {
          //should be handled on assignment
          throw new NeedCompilationException("Update method is not supported")
        }
      }
      val message = ScalaBundle.message("cannot.evaluate.method", call.getText)
      call.getInvokedExpr match {
        case ref: ScReferenceExpression =>
          myResult = methodCallEvaluator(parentCall, call.argumentExpressions ++ collected, matchedParameters ++ call.matchedParametersMap)
        case newCall: ScMethodCall =>
          collectArgumentsAndBuildEvaluator(newCall, call.argumentExpressions ++ collected, call.args.getText + tailString,
            matchedParameters ++ call.matchedParametersMap)
        case gen: ScGenericCall =>
          gen.referencedExpr match {
            case ref: ScReferenceExpression if ref.resolve().isInstanceOf[PsiMethod] =>
              myResult = methodCallEvaluator(parentCall, call.argumentExpressions ++ collected, matchedParameters ++ call.matchedParametersMap)
            case ref: ScReferenceExpression =>
              ref.getType().getOrAny match {
                //isApplyOrUpdateCall does not work for generic calls
                case ScType.ExtractClass(psiClass) if psiClass.findMethodsByName("apply", true).nonEmpty =>
                  val typeArgsText = gen.typeArgs.fold("")(_.getText)
                  val expr = applyCall(ref.getText, s"$typeArgsText${call.args.getText}$tailString")
                  myResult = ScalaEvaluator(expr)
                case _ => throw EvaluationException(message)
              }
            case _ =>
              throw EvaluationException(message)

          }
        case _ => throw EvaluationException(message)
      }
    }

    parentCall match {
      case hasDeepestInvokedReference(ScReferenceExpression.withQualifier(implicitlyConvertedTo(expr))) =>
        val copy = parentCall.copy().asInstanceOf[ScMethodCall]
        copy match {
          case hasDeepestInvokedReference(ScReferenceExpression.withQualifier(q)) =>
            q.replaceExpression(expr, removeParenthesis = false)
            myResult = ScalaEvaluator(copy)
          case _ =>
            val message = ScalaBundle.message("method.call.implicitly.converted.qualifier", parentCall.getText)
            throw EvaluationException(message)
        }
      case _ =>
        //todo: handle partially applied functions
        collectArgumentsAndBuildEvaluator(parentCall)
    }
    super.visitMethodCallExpression(parentCall)
  }

  override def visitInfixExpression(infix: ScInfixExpr) {
    val operation = infix.operation
    def isUpdate(ref: ScReferenceExpression): Boolean = {
      ref.refName.endsWith("=") &&
              (ref.resolve() match {
                case n: PsiNamedElement if n.name + "=" == ref.refName => true
                case _ => false
              })
    }

    if (isUpdate(operation)) {
      val baseExprText = infix.getBaseExpr.getText
      val operationText = operation.refName.dropRight(1)
      val argText = infix.getArgExpr.getText
      val exprText = s"$baseExprText = $baseExprText $operationText $argText"
      val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, infix.getContext, infix)
      myResult = ScalaEvaluator(expr)
    }
    else {
      val equivCall = ScalaPsiElementFactory.createEquivMethodCall(infix)
      myResult = ScalaEvaluator(equivCall)
    }
    super.visitInfixExpression(infix)
  }

  //this is the only method which can modify myResult after it was set
  override def visitExpression(expr: ScExpression) {

    //boxing and unboxing actions
    def unbox(typeTo: String) = {myResult = unaryEvaluator(unboxEvaluator(myResult), typeTo)}
    def box() {myResult = boxEvaluator(myResult)}

    import org.jetbrains.plugins.scala.lang.psi.types._

    expr.smartExpectedType() match {
      case Some(Int) => unbox("toInteger")
      case Some(Byte) => unbox("toByte")
      case Some(Long) => unbox("toLong")
      case Some(Boolean) => myResult = unboxEvaluator(myResult)
      case Some(Float) => unbox("toFloat")
      case Some(Short) => unbox("toShort")
      case Some(Double) => unbox("toDouble")
      case Some(Char) => unbox("toCharacter")
      case Some(Unit) => myResult = new BlockStatementEvaluator(Array(myResult, unitEvaluator()))
      case None => //nothing to do
      case _ => box()
    }

    expr match {
      case _: ScNewTemplateDefinition =>
      case ExpressionType(_: ValType) =>
      case ExpressionType(tp @ ValueClassType(inner))  =>
        myResult = valueClassInstanceEvaluator(myResult, inner, tp)
      case _ =>
    }
  }

  override def visitVariableDefinition(varr: ScVariableDefinition) {
    throw new NeedCompilationException("Evaluation of variables is not supported")
  }

  override def visitTupleExpr(tuple: ScTuple) {
    val exprText = "_root_.scala.Tuple" + tuple.exprs.length + tuple.exprs.map(_.getText).mkString("(", ", ", ")")
    val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, tuple.getContext, tuple)
    myResult = ScalaEvaluator(expr)
    super.visitTupleExpr(tuple)
  }

  override def visitPatternDefinition(pat: ScPatternDefinition) {
    throw new NeedCompilationException("Evaluation of values is not supported")
  }

  override def visitTypeDefinition(typedef: ScTypeDefinition) {
    throw new NeedCompilationException("Evaluation of local classes is not supported")
  }

  override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) {
    templ.extendsBlock.templateBody match {
      case Some(tb) => throw new NeedCompilationException("Anonymous classes are not supported")
      case _ =>
    }
    myResult = newTemplateDefinitionEvaluator(templ)

    super.visitNewTemplateDefinition(templ)
  }
}

private[evaluation] object ScalaEvaluator {
  def apply(element: PsiElement, contextClass: Option[PsiClass] = None)
           (implicit position: SourcePosition): Evaluator = {
    new EvaluatorBuilderVisitor(element, contextClass).getEvaluator
  }
}