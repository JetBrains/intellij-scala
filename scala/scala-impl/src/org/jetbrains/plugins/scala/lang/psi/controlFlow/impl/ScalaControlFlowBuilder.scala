package org.jetbrains.plugins.scala
package lang.psi.controlFlow.impl

import _root_.org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

class ScalaControlFlowBuilder(startInScope: ScalaPsiElement,
                              endInsScope: ScalaPsiElement)
        extends ScalaRecursiveElementVisitor {
  private val myInstructionsBuilder = ArraySeq.newBuilder[InstructionImpl]
  private val myPending = new ArrayBuffer[(InstructionImpl, ScalaPsiElement)]
  private val myTransitionInstructions = new ArrayBuffer[(InstructionImpl, HandleInfo)]
  private var myCatchedExnStack = List.empty[HandleInfo]
  private var myInstructionNum = 0
  private var myHead: InstructionImpl = _


  def buildControlflow(scope: ScalaPsiElement): Seq[Instruction] = {
    // initial node
    val instr = new InstructionImpl(inc, None)
    addNode(instr)
    scope.accept(this)
    // final node
    emptyNode()
    myInstructionsBuilder.result()
  }

  def inc: Int = {
    val num = myInstructionNum
    myInstructionNum += 1
    num
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    if ((element eq startInScope) || !(element eq endInsScope)) super.visitScalaElement(element)
  }

  def emptyNode(): Unit = {
    startNode(None) {
      _ =>
    }
  }

  def startNode(element: Option[ScalaPsiElement])(body: InstructionImpl => Unit): Unit = {
    startNode(element, checkPending = true)(body)
  }

  /**
   * Process a new node inside the CFG
   */
  private def startNode(element: Option[ScalaPsiElement], checkPending: Boolean)(body: InstructionImpl => Unit): Unit = {
    val instr = new InstructionImpl(inc, element)
    addNode(instr)
    body(instr)
    if (checkPending) checkPendingEdges(instr)
  }

  private def addNode(instr: InstructionImpl): Unit = {
    myInstructionsBuilder += instr
    if (myHead != null) addEdge(myHead, instr)
    myHead = instr
  }

  private def addEdge(from: InstructionImpl, to: InstructionImpl): Unit = {
    if (from == null || to == null) return
    if (!from.succ.contains(to)) from.addSucc(to)
    if (!to.pred.contains(from)) to.addPred(from)
  }

  private def checkPendingEdges(instruction: InstructionImpl): Unit = {
    instruction.element match {
      case Some(elem) =>
        val ab = new ArrayBuffer[Int]
        for (i <- myPending.size - 1 to (0, -1)) {
          val (inst, scope) = myPending(i)
          if (scope != null &&
                  !PsiTreeUtil.isAncestor(scope, elem, false)) {
            addEdge(inst, instruction)
            ab += i
          }
        }
        // remove registered pending edges
        for (k <- ab) myPending.remove(k)
      case None =>
        for ((from, _) <- myPending) addEdge(from, instruction)
        myPending.clear()
    }
  }

  private def addPendingEdge(scopeWhenAdded: ScalaPsiElement, instruction: InstructionImpl): Unit = {
    if (instruction == null) return
    var index = 0
    if (scopeWhenAdded != null) {
      index = myPending.indexWhere {case (_, e) => !PsiTreeUtil.isAncestor(e, scopeWhenAdded, true)}
    }
    if (!myPending.contains((instruction, scopeWhenAdded)))
      myPending.insert(math.max(index, 0), (instruction, scopeWhenAdded))
  }

  private def advancePendingEdges(fromScope: ScalaPsiElement, toScope: ScalaPsiElement): Unit = {
    for {
      ((instr, scope), idx) <- myPending.zipWithIndex
      if scope != null && PsiTreeUtil.isAncestor(fromScope, scope, false)
    } {
      myPending.update(idx, (instr, toScope))
    }
  }

  private def interruptFlow(): Unit = {
    myHead = null
  }

  private def moveHead(instr: InstructionImpl): Unit = {
    myHead = instr
  }

  def usedVariable(ref: ScReference): Option[PsiNamedElement] = ref.resolve() match {
    case named: PsiNamedElement => Some(named)
    case _ => None
  }

  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition): Unit = {
    pattern.expr.foreach(_.accept(this))
    for (b <- pattern.bindings) {
      val instr = DefinitionInstruction(inc, b, DefinitionType.VAL)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition): Unit = {
    variable.expr.foreach(_.accept(this))
    for (b <- variable.bindings) {
      val instr = DefinitionInstruction(inc, b, DefinitionType.VAR)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
    ref.qualifier match {
      case None =>
        val instr = ReadWriteVariableInstruction(inc, ref, usedVariable(ref), ScalaPsiUtil.isLValue(ref))
        addNode(instr)
        checkPendingEdges(instr)
      case Some(qual) => qual.accept(this)
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
    startNode(Some(stmt)) {doStmtInstr =>
      checkPendingEdges(doStmtInstr)
      stmt.body foreach { e =>
        e.accept(this)
      }
      stmt.condition foreach { c =>
        c.accept(this)
        if (myHead != null) {
          checkPendingEdges(myHead)
          addEdge(myHead, doStmtInstr)
          addPendingEdge(stmt, myHead)
        }
      }
    }
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    cc.pattern match {
      case Some(p) => for (b <- p.bindings) {
        val instr = DefinitionInstruction(inc, b, DefinitionType.VAL)
        checkPendingEdges(instr)
        addNode(instr)
      }
      case None =>
    }

    cc.guard match {
      case Some(g) => g.accept(this) // todo implement Guard PSI
      case _ =>
    }

    cc.expr match {
      case Some(e) => e.accept(this)
      case _ =>
    }
  }

  override def visitMatch(ms: ScMatch): Unit = {
    startNode(Some(ms)) {msInstr =>
      checkPendingEdges(msInstr)
      ms.expression match {
        case Some(e) => e.accept(this)
        case _ =>
      }
      for (cc <- ms.clauses) {
        cc.accept(this)
        advancePendingEdges(cc, ms)
        addPendingEdge(ms, myHead)
        moveHead(msInstr)
      }
    }
  }

  override def visitWhile(ws: ScWhile): Unit = {
    startNode(Some(ws)) {instr =>
      checkPendingEdges(instr)
      // for breaks
      //addPendingEdge(ws, myHead)
      ws.condition.foreach(_.accept(this))
      ws.expression.foreach { b =>
        b.accept(this)
      }
      checkPendingEdges(instr)
      // add backward edge
      if (myHead != null) addEdge(myHead, instr)
      moveHead(instr)
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = {
    val matchedParams = call.matchedParameters
    def isByNameOrFunction(arg: ScExpression) = {
      val param = matchedParams.toMap.get(arg)
      param.isEmpty || param.exists(_.isByName) || param.exists(p => FunctionType.isFunctionType(p.paramType))
    }
    val receiver = call.getInvokedExpr
    if (receiver != null) {
      receiver.accept(this)
    }
    val head = myHead
    if (head != null)
      checkPendingEdges(head)
    for {
      arg <- call.argumentExpressions
    } {
      arg.accept(this)
      if (myHead == null && isByNameOrFunction(arg)) {
        moveHead(head)
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
        val instr = DefinitionInstruction(inc, b, DefinitionType.VAL)
        checkPendingEdges(instr)
        addNode(instr)
      case _ => super.visitPattern(pat)
    }
  }

  override def visitForBinding(forBinding: ScForBinding): Unit = {
    forBinding.expr.foreach(_.accept(this))
    val pat = forBinding.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitFor(forStmt: ScFor): Unit = {
    startNode(Some(forStmt)) {instr =>
      checkPendingEdges(instr)
      addPendingEdge(forStmt, myHead)
      forStmt.enumerators match {
        case Some(enum) => enum.accept(this)
        case _ =>
      }
      forStmt.body match {
        case Some(e) =>
          e.accept(this)
          advancePendingEdges(e, forStmt)
        case _ =>
      }
      addPendingEdge(forStmt, myHead)
    }
  }

  override def visitIf(stmt: ScIf): Unit = {
    startNode(Some(stmt)) {ifStmtInstr =>
      checkPendingEdges(ifStmtInstr)
      stmt.condition match {
        case Some(cond) =>
          cond.accept(this)
        case None =>
      }

      val head = Option(myHead).getOrElse(ifStmtInstr)

      // reduced if-then expression (without `else`)
      stmt.elseExpression match {
        case None => addPendingEdge(stmt, head)
        case _ =>
      }

      stmt.thenExpression match {
        case Some(tb) =>
          tb.accept(this)
          advancePendingEdges(tb, stmt)
          addPendingEdge(stmt, myHead)
          myHead = head
        case None =>
      }
      stmt.elseExpression match {
        case Some(eb) =>
          eb.accept(this)
          advancePendingEdges(eb, stmt)
          addPendingEdge(stmt, myHead)
        case _ =>
      }
    }
  }

  override def visitReturn(ret: ScReturn): Unit = {
    val isNodeNeeded = myHead == null || (myHead.element match {
      case Some(e) => e != ret
      case None => false
    })
    ret.expr.foreach(_.accept(this))

    if (isNodeNeeded) startNode(Some(ret)) { _ =>
      addPendingEdge(null, myHead)
    }
    else addPendingEdge(null, myHead)

    // add edge to finally block
    getClosestFinallyInfo.map {finfo => myTransitionInstructions += ((myHead, finfo))}
    interruptFlow()
  }

  override def visitFunctionExpression(stmt: ScFunctionExpr): Unit = addFreeVariables(stmt)


  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = { /* Do not visit inner classes either */ }

  override def visitBlockExpression(block: ScBlockExpr): Unit = {
    if (block.isAnonymousFunction) {
      // Do not visit closures
    } else super.visitBlockExpression(block)
  }


  override def visitInfixExpression(infix: ScInfixExpr): Unit = {
    def accept(): Unit = {
      val ScInfixExpr.withAssoc(base, operation, argument) = infix
      base.accept(this)
      operation.accept(this)
      argument.accept(this)
    }

    val byNameParam = infix.matchedParameters.exists(_._2.isByName)
    if (byNameParam) startNode(Some(infix)) { infixInstr =>
      checkPendingEdges(infixInstr)
      addPendingEdge(infix, infixInstr)
      accept()
      if (myHead == null) moveHead(infixInstr)
    } else accept()
  }

  override def visitFunction(fun: ScFunction): Unit = {
    val instr = DefinitionInstruction(inc, fun, DefinitionType.DEF)
    checkPendingEdges(instr)
    addNode(instr)

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
      val instr = ReadWriteVariableInstruction(inc, ref, usedVariable(ref), ScalaPsiUtil.isLValue(ref))
      addNode(instr)
      checkPendingEdges(instr)
    }
  }

  override def visitThrow(throwStmt: ScThrow): Unit = {
    val isNodeNeeded = myHead == null || (myHead.element match {
      case Some(e) => e != throwStmt
      case None => false
    })
    throwStmt.expression.foreach(_.accept(this))
    if (isNodeNeeded) startNode(Some(throwStmt)) { _ =>
      addPendingEdge(null, myHead)
    }
    else addPendingEdge(null, myHead)
    interruptFlow()
  }

  private def getClosestFinallyInfo = myCatchedExnStack.collectFirst {case fi: FinallyInfo => fi}

  sealed abstract class HandleInfo(val elem: ScalaPsiElement)
  case class CatchInfo(cc: ScCaseClause) extends HandleInfo(cc)
  case class FinallyInfo(fb: ScFinallyBlock) extends HandleInfo(fb)

  override def visitTry(tryStmt: ScTry): Unit = {
    val handledExnTypes = tryStmt.catchBlock match {
      case None => Nil
      case Some(cb) => cb.expression match {
        case Some(b: ScBlockExpr) if b.hasCaseClauses =>
          for (t <- b.caseClauses.toSeq.flatMap(_.caseClauses)) yield CatchInfo(t)
        case _ => Nil
      }
    }
    myCatchedExnStack = handledExnTypes.toList.reverse ++ myCatchedExnStack
    var catchedExnCount = handledExnTypes.size

    val fBlock = tryStmt.finallyBlock match {
      case None => null
      case Some(x) => x
    }
    if (fBlock != null) {
      myCatchedExnStack = FinallyInfo(fBlock) :: myCatchedExnStack
      catchedExnCount += 1
    }

    startNode(Some(tryStmt)) {tryStmtInstr =>
      checkPendingEdges(tryStmtInstr)
      // process try block
      tryStmt.expression.foreach { tb =>
        tb.accept(this)
        val head = Option(myHead).getOrElse(tryStmtInstr)
        advancePendingEdges(tb, tryStmt)
        tryStmt.finallyBlock.fold {
          addPendingEdge(tryStmt, head)
        } {
          fblock => myTransitionInstructions += ((head, FinallyInfo(fblock)))
        }
      }

      // remove exceptions
      for (_ <- 1 to catchedExnCount) {
        myCatchedExnStack = myCatchedExnStack.tail
      }

      def processCatch(fin: InstructionImpl): Unit = tryStmt.catchBlock.foreach { cb =>
        cb.expression match {
          case Some(b: ScBlockExpr) if b.hasCaseClauses =>
            for (cc <- b.caseClauses.toSeq.flatMap(_.caseClauses)) {
              myHead = tryStmtInstr
              cc.accept(this)
              if (fin == null) {
                advancePendingEdges(cc, tryStmt)
                addPendingEdge(tryStmt, myHead)
              } else {
                addEdge(myHead, fin)
              }
              myHead = null
            }
          case _ =>
            for (cc <- cb.expression) {
              myHead = tryStmtInstr
              cc.accept(this)
              if (fin == null) {
                advancePendingEdges(cc, tryStmt)
                addPendingEdge(tryStmt, myHead)
              } else {
                addEdge(myHead, fin)
              }
              myHead = null
            }
        }
      }

      if (fBlock == null) {
        processCatch(null)
      } else {
        startNode(Some(fBlock)) {finInstr =>
          for (p@(instr, info) <- myTransitionInstructions; if info.elem eq fBlock) {
            addEdge(instr, finInstr)
            myTransitionInstructions -= p
          }
          processCatch(finInstr)
          myHead = finInstr
          fBlock.accept(this)
        }
      }
    }
  }
}
