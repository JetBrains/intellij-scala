package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl

import _root_.org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScCaseClauses, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder.{CatchScope, DeferredScope, FinallyScope, InstructionBuilder}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ScalaControlFlowBuilder(startInScope: ScalaPsiElement,
                              endInsScope: ScalaPsiElement)
  extends ScalaRecursiveElementVisitor {

  private val builder = new InstructionBuilder

  def buildControlflow(scope: ScalaPsiElement): Seq[Instruction] = {
    // initial node
    builder.addEmptyInstr()
    scope.accept(this)
    // final node
    builder.addEmptyInstr()
    builder.result()
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    if ((element eq startInScope) || !(element eq endInsScope)) super.visitScalaElement(element)
  }

  private def usedVariable(ref: ScReference): Option[PsiNamedElement] = ref.resolve() match {
    case named: PsiNamedElement => Some(named)
    case _ => None
  }

  private def inImplicitConversion[R](expr: ScExpression)(f: => R): R = {
    expr.implicitElement() match {
      case Some(elem: ScParameterOwner) if elem.parameters.headOption.exists(_.isCallByNameParameter) =>
        val beforeConversion = builder.getPending
        try f
        finally builder.connectHereFrom(beforeConversion)
      case _ => f
    }
  }

  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition): Unit = {
    val isLazy = pattern.getModifierList.isLazy

    val beforeBinding = builder.getPending
    pattern.expr.foreach(_.accept(this))
    if (isLazy) {
      builder.connectHereFrom(beforeBinding)
    }

    for (b <- pattern.bindings) {
      builder.addDefInstr(b, DefinitionType.VAL)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition): Unit = {
    variable.expr.foreach(_.accept(this))
    for (b <- variable.bindings) {
      builder.addDefInstr(b, DefinitionType.VAR)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression): Unit = inImplicitConversion(ref) {
    ref.qualifier match {
      case None =>
        builder.addRWInstr(ref, usedVariable(ref), ScalaPsiUtil.isLValue(ref))
      case Some(qual) =>
        qual.accept(this)
    }
  }

  override def visitAssignment(stmt: ScAssignment): Unit = {
    stmt.rightExpression match {
      case Some(rv) =>
        rv.accept(this)
        stmt.getParent match {
          case _: ScArgumentExprList =>
          case _ =>
            val lValue = stmt.leftExpression
            lValue.accept(this)
        }
      case _ =>
    }
  }

  override def visitDo(stmt: ScDo): Unit = {
    val doInstr = builder.addElementInstr(stmt)
    stmt.body foreach { e =>
      e.accept(this)
    }
    stmt.condition foreach { c =>
      c.accept(this)
    }
    builder.connectHereTo(doInstr)
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    for {
      pattern <- cc.pattern
      binding <- pattern.bindings
    } builder.addDefInstr(binding, DefinitionType.VAL)

    cc.guard.foreach(_.accept(this)) // todo implement Guard PSI
    cc.expr.foreach(_.accept(this))
  }

  override def visitMatch(ms: ScMatch): Unit = {
    builder.addElementInstr(ms)
    ms.expression match {
      case Some(e) => e.accept(this)
      case _ =>
    }

    ms.caseClauses.foreach(_.accept(this))
  }

  override def visitCaseClauses(ccs: ScCaseClauses): Unit = {
    val fromBeforeClauses = builder.getPending
    val openJumps =
      for (cc <- ccs.caseClauses) yield {
        builder.connectHereFrom(fromBeforeClauses)
        cc.accept(this)
        builder.jumpAway()
      }
    builder.connectHereFrom(openJumps)
  }

  override def visitWhile(ws: ScWhile): Unit = {
    val whileInstr = builder.addElementInstr(ws)
    ws.condition.foreach(_.accept(this))
    ws.expression.foreach(_.accept(this))
    builder.jumpTo(whileInstr)
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = {
    val matchedParams = call.matchedParameters
    def isByNameOrFunction(arg: ScExpression) = {
      val param = matchedParams.find(_._1 == arg).map(_._2)
      param.isEmpty || param.exists(_.isByName) || param.exists(p => FunctionType.isFunctionType(p.paramType))
    }
    call.getInvokedExpr.accept(this)
    for {
      arg <- call.argumentExpressions
    } {
      val pending = builder.getPending
      if (isByNameOrFunction(arg)) {
        val (_, catchesInsideInvokedExpr) = builder.withScope(DeferredScope) {
          arg.accept(this)
        }
        builder.connectHereFrom(catchesInsideInvokedExpr)
        builder.connectHereFrom(pending)
      } else {
        arg.accept(this)
      }
    }
  }

  override def visitGenerator(gen: ScGenerator): Unit = {
    gen.expr.foreach(_.accept(this))
    gen.pattern.accept(this)
  }

  override def visitGuard(guard: ScGuard): Unit = {
    guard.expr match {
      case Some(e) => e.accept(this)
      case _ =>
    }
  }

  override def visitPattern(pat: ScPattern): Unit = {
    pat match {
      case b: ScBindingPattern =>
        builder.addDefInstr(b, DefinitionType.VAL)
      case _ => super.visitPattern(pat)
    }
  }

  override def visitForBinding(forBinding: ScForBinding): Unit = {
    forBinding.expr.foreach(_.accept(this))
    val pat = forBinding.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitFor(forStmt: ScFor): Unit = {
    val forInstr = builder.addElementInstr(forStmt)
    forStmt.enumerators.foreach(_.accept(this))
    forStmt.body.foreach(_.accept(this))
    builder.jumpTo(forInstr)
  }

  override def visitIf(stmt: ScIf): Unit = {
    val ifInstr = builder.addElementInstr(stmt)
    stmt.condition.foreach(_.accept(this))

    val afterIf = builder.getPending match {
      case p if p.isEmpty => Set(ifInstr)
      case p => p
    }

    stmt.thenExpression.foreach(_.accept(this))
    val afterThen = builder.jumpAway()

    builder.connectHereFrom(afterIf)
    stmt.elseExpression.foreach(_.accept(this))
    builder.connectHereFrom(afterThen)
  }

  override def visitReturn(ret: ScReturn): Unit = {
    ret.expr.foreach(_.accept(this))
    builder.addElementInstr(ret)
    builder.jumpAwayToScope(_.catchesReturns)
  }

  override def visitFunctionExpression(stmt: ScFunctionExpr): Unit = addFreeVariables(stmt)


  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = { /* Do not visit inner classes either */ }

  override def visitBlockExpression(block: ScBlockExpr): Unit = {
    if (block.isPartialFunction) {
      // Do not visit closures
    } else inImplicitConversion(block) {
      super.visitBlockExpression(block)
    }
  }


  override def visitInfixExpression(infix: ScInfixExpr): Unit = inImplicitConversion(infix) {
    val byNameParam = infix.matchedParameters.headOption.exists(_._2.isByName)
    if (byNameParam) {
      val ScInfixExpr.withAssoc(base, operation, argument) = infix
      base.accept(this)
      operation.accept(this)
      val beforeArgument = builder.getPending
      argument.accept(this)
      builder.connectHereFrom(beforeArgument)
    } else {
      infix.left.accept(this)
      infix.operation.accept(this)
      infix.rightOption.foreach(_.accept(this))
    }
  }

  override def visitFunction(fun: ScFunction): Unit = {
    builder.addDefInstr(fun, DefinitionType.DEF)
    addFreeVariables(fun)
  }

  private def addFreeVariables(paramOwner: ScalaPsiElement): Unit = {
    val parameters = paramOwner match {
      case owner: ScParameterOwner => owner.parameters
      case ScFunctionExpr(params, _) => params
      case _ => return
    }
    val collectedRefs = ArrayBuffer[ScReferenceExpression]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (ref.qualifier.nonEmpty) return

        ref.bind() match {
          case Some(srr @ ScalaResolveResult(p: ScParameter, _))
              if parameters.contains(p) || srr.isNamedParameter => ()
          case Some(ScalaResolveResult(named: PsiNamedElement, _))
              if !PsiTreeUtil.isAncestor(paramOwner, named, false) => collectedRefs += ref
          case _ => ()
        }
      }
    }

    paramOwner.accept(visitor)

    for (ref <- collectedRefs) {
      builder.addRWInstr(ref, usedVariable(ref), ScalaPsiUtil.isLValue(ref))
    }
  }

  override def visitThrow(throwStmt: ScThrow): Unit = {
    throwStmt.expression.foreach(_.accept(this))
    builder.addElementInstr(throwStmt)
    builder.jumpAwayToScope(_.catchesExceptions)
  }

  override def visitTry(tryStmt: ScTry): Unit = {
    val tryInstr = builder.addElementInstr(tryStmt)

    val (fromTryExprAndCatch, jumpsToFinally) = builder.withScope(FinallyScope) {
      val (_, jumpsToCatch) = builder.withScope(CatchScope) {
        tryStmt.expression.foreach(_.accept(this))
      }
      val tryExprEnding = builder.jumpAway()

      tryStmt.catchBlock match {
        case Some(catchBlock) =>
          builder.connectHereFrom(jumpsToCatch)
          builder.connectHereFrom(tryInstr)
          builder.addElementInstr(catchBlock)
          catchBlock.accept(this)
          tryExprEnding ++ builder.jumpAway()
        case None =>
          tryExprEnding
      }
    }

    builder.connectHereFrom(fromTryExprAndCatch)
    builder.connectHereFrom(jumpsToFinally)
    builder.connectHereFrom(tryInstr)
    tryStmt.finallyBlock.foreach { fBlock =>
      builder.addElementInstr(fBlock)
      fBlock.accept(this)
    }
    // this is not completely correct
    // but for the unreachability analysis
    // we don't want an edge from the finally block to the code afterwards
    // otherwise finally would have the same effect as a catch
    builder.interrupt()

    builder.connectHereFrom(fromTryExprAndCatch)
  }
}

object ScalaControlFlowBuilder {
  private class InstructionBuilder {
    private val instructions = ArraySeq.newBuilder[InstructionImpl]
    // `pending` contains all instructions that will have control flow to the instruction that is added next
    private var pending = Set.empty[InstructionImpl]
    private var nextInstructionNum = 0
    private var catchStack = List.empty[(Scope, mutable.Builder[InstructionImpl, Set[InstructionImpl]])]

    private def addInstruction(instr: InstructionImpl): InstructionImpl = {
      assert(instr.num == nextInstructionNum)
      nextInstructionNum += 1
      instructions += instr
      jumpTo(instr)
      instr
    }

    def addEmptyInstr(): InstructionImpl =
      addInstruction(new ElementInstruction(nextInstructionNum, None))

    def addElementInstr(elem: ScalaPsiElement): InstructionImpl =
      addInstruction(new ElementInstruction(nextInstructionNum, Some(elem)))

    def addDefInstr(namedElement: ScNamedElement, defType: DefinitionType): InstructionImpl =
      addInstruction(DefinitionInstruction(namedElement, defType)(nextInstructionNum))

    def addRWInstr(ref: ScReferenceExpression, variable: Option[PsiNamedElement], write: Boolean): InstructionImpl =
      addInstruction(ReadWriteVariableInstruction(ref, variable, write)(nextInstructionNum))

    def jumpTo(target: InstructionImpl): Unit = {
      connectHereTo(target)
      pending = Set(target)
    }

    def jumpAway(): Set[InstructionImpl] = {
      val p = pending
      pending = Set.empty
      p
    }

    def connectHereFrom(instr: InstructionImpl): Unit = {
      pending += instr
    }

    def connectHereFrom(p: Set[InstructionImpl]): Unit = {
      pending ++= p
    }

    def connectHereFrom(pp: Seq[Set[InstructionImpl]]): Unit = {
      pp.foreach(connectHereFrom)
    }

    def connectHereTo(target: InstructionImpl): Unit = {
      for (prev <- pending) {
        InstructionImpl.addEdge(prev, target)
      }
    }

    def interrupt(): Unit =
      pending = Set.empty

    def getPending: Set[InstructionImpl] =
      pending

    def withScope[R](scope: Scope)(f: => R): (R, Set[InstructionImpl]) = {
      val savedCatches = catchStack
      val builder = Set.newBuilder[InstructionImpl]
      catchStack ::= (scope, builder)
      val result = f
      catchStack = savedCatches
      (result, builder.result())
    }

    def jumpAwayToScope(f: Scope => Boolean): Unit = {
      catchStack.find(s => f(s._1)).foreach(_._2 ++= pending)
      pending = Set.empty
    }

    def result(): Seq[Instruction] = {
      val result = instructions.result()
      assert(result.size == nextInstructionNum)
      result
    }
  }

  private sealed abstract class Scope(val catchesExceptions: Boolean, val catchesReturns: Boolean)

  private case object FinallyScope extends Scope(true, true)
  private case object CatchScope extends Scope(true, false)
  // A scope for expressions that may be executed inside another function.
  // Technically, these scopes may also catch returns via NonLocalReturnControl-throwable,
  // but I think we can assume that this is not the case
  private case object DeferredScope extends Scope(true, false)
}