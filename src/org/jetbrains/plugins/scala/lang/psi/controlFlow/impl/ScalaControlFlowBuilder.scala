package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl


import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{ScControlFlowPolicy, Instruction}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariableDefinition, ScPatternDefinition}
import collection.mutable.ArrayBuffer
import scala.collection.mutable

/**
 * @author ilyas
 */

class ScalaControlFlowBuilder(startInScope: ScalaPsiElement,
                              endInsScope: ScalaPsiElement,
                              policy: ScControlFlowPolicy = AllVariablesControlFlowPolicy)
        extends ScalaRecursiveElementVisitor {
  private val myInstructions = new ArrayBuffer[InstructionImpl]
  private val myPending = new ArrayBuffer[(InstructionImpl, ScalaPsiElement)]
  private val myTransitionInstructions = new ArrayBuffer[(InstructionImpl, HandleInfo)]
  private val myCatchedExnStack = new mutable.Stack[HandleInfo]
  private var myInstructionNum = 0
  private var myHead: InstructionImpl = null


  def buildControlflow(scope: ScalaPsiElement): Seq[Instruction] = {
    // initial node
    val instr = new InstructionImpl(inc, None)
    addNode(instr)
    scope.accept(this)
    // final node
    emptyNode()
    myInstructions.toSeq
  }

  def inc = {
    val num = myInstructionNum
    myInstructionNum += 1
    num
  }

  override def visitElement(element: ScalaPsiElement) {
    if ((element eq startInScope) || !(element eq endInsScope)) super.visitElement(element)
  }

  def emptyNode() {
    startNode(None) {
      _ =>
    }
  }

  def startNode(element: Option[ScalaPsiElement])(body: InstructionImpl => Unit) {
    startNode(element, checkPending = true)(body)
  }

  /**
   * Process a new node inside the CFG
   */
  private def startNode(element: Option[ScalaPsiElement], checkPending: Boolean)(body: InstructionImpl => Unit) {
    val instr = new InstructionImpl(inc, element)
    addNode(instr)
    body(instr)
    if (checkPending) checkPendingEdges(instr)
  }

  private def addNode(instr: InstructionImpl) {
    myInstructions += instr
    if (myHead != null) addEdge(myHead, instr)
    myHead = instr
  }

  private def addEdge(from: InstructionImpl, to: InstructionImpl) {
    if (from == null || to == null) return
    if (!from.succ().contains(to)) from.addSucc(to)
    if (!to.pred().contains(from)) to.addPred(from)
  }

  private def checkPendingEdges(instruction: InstructionImpl) {
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

  private def addPendingEdge(scopeWhenAdded: ScalaPsiElement, instruction: InstructionImpl) {
    if (instruction == null) return
    var index = 0
    if (scopeWhenAdded != null) {
      index = myPending.indexWhere {case (_, e) => !PsiTreeUtil.isAncestor(e, scopeWhenAdded, true)}
    }
    myPending.insert(math.max(index, 0), (instruction, scopeWhenAdded))
  }

  private def interruptFlow() {
    myHead = null
  }

  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition) {
    pattern.expr.foreach(_.accept(this))
    for (b <- pattern.bindings if policy.isElementAccepted(b)) {
      val instr = new DefineValueInstruction(inc, b, false)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition) {
    variable.expr.foreach(_.accept(this))
    for (b <- variable.bindings if policy.isElementAccepted(b)) {
      val instr = new DefineValueInstruction(inc, b, true)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) {
    ref.qualifier match {
      case None if policy.isElementAccepted(ref.resolve()) =>
        val instr = new ReadWriteVariableInstruction(inc, ref, ScalaPsiUtil.isLValue(ref))
        addNode(instr)
        checkPendingEdges(instr)
      case Some(qual) => qual.accept(this)
    }
  }

  override def visitAssignmentStatement(stmt: ScAssignStmt) {
    val lValue = stmt.getLExpression
    stmt.getRExpression match {
      case Some(rv) =>
        rv.accept(this)
        lValue.accept(this)
      case _ =>
    }
  }

  override def visitDoStatement(stmt: ScDoStmt) {
    startNode(Some(stmt)) {i =>
      checkPendingEdges(i)
      stmt.getExprBody map {e =>
        e.accept(this)
        addPendingEdge(stmt, myHead)
      }
      stmt.condition map {c =>
        c.accept(this)
        if (myHead != null) {
          addEdge(myHead, i)
        }
      }
      interruptFlow()
    }
  }

  override def visitCaseClause(cc: ScCaseClause) {
    cc.pattern match {
      case Some(p) => for (b <- p.bindings if policy.isElementAccepted(b)) {
        val instr = new DefineValueInstruction(inc, b, false)
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

  override def visitMatchStatement(ms: ScMatchStmt) {
    startNode(Some(ms)) {instr =>
      checkPendingEdges(instr)
      ms.expr match {
        case Some(e) => e.accept(this)
        case _ =>
      }
      val head = myHead
      for (cc <- ms.caseClauses) {
        myHead = head
        cc.accept(this)
        addPendingEdge(ms, myHead)
        myHead = null
      }
    }
  }

  override def visitWhileStatement(ws: ScWhileStmt) {
    startNode(Some(ws)) {instr =>
      checkPendingEdges(instr)
      // for breaks
      addPendingEdge(ws, myHead)
      ws.condition.foreach(_.accept(this))
      ws.body.foreach {b =>
        b.accept(this)
      }
      checkPendingEdges(instr)
      // add backward edge
      if (myHead != null) addEdge(myHead, instr)
      interruptFlow()
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall) {
    for (arg <- call.argumentExpressions) arg.accept(this)
    val receiver = call.getInvokedExpr
    if (receiver != null) {
      receiver.accept(this)
    }
  }

  override def visitGenerator(gen: ScGenerator) {
    val rv = gen.rvalue
    if (rv != null) rv.accept(this)
    val pat = gen.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitGuard(guard: ScGuard) {
    guard.expr match {
      case Some(e) => e.accept(this)
      case _ =>
    }
  }

  override def visitPattern(pat: ScPattern) {
    pat match {
      case b: ScBindingPattern if policy.isElementAccepted(b) =>
        val instr = new DefineValueInstruction(inc, b, false)
        checkPendingEdges(instr)
        addNode(instr)
      case _ => super.visitPattern(pat)
    }
  }

  override def visitEnumerator(enum: ScEnumerator) {
    val rv = enum.rvalue
    if (rv != null) rv.accept(this)
    val pat = enum.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitForExpression(expr: ScForStatement) {
    startNode(Some(expr)) {instr =>
      checkPendingEdges(instr)
      addPendingEdge(expr, myHead)
      expr.enumerators match {
        case Some(enum) => enum.accept(this)
        case _ =>
      }
      expr.body match {
        case Some(e) => e.accept(this)
        case _ =>
      }
      addPendingEdge(expr, myHead)
    }
  }

  override def visitIfStatement(stmt: ScIfStmt) {
    startNode(Some(stmt)) {instr =>
      checkPendingEdges(instr)
      stmt.condition match {
        case Some(cond) =>
          cond.accept(this)
        case None =>
      }

      // reduced if-then expression (without `else`)
      stmt.elseBranch match {
        case None => addPendingEdge(stmt, myHead)
        case _ =>
      }

      val head = myHead
      stmt.thenBranch match {
        case Some(tb) =>
          tb.accept(this)
          // the context will be refined later
          addPendingEdge(stmt, myHead)
        case None =>
      }
      stmt.elseBranch match {
        case Some(eb) =>
          myHead = head
          eb.accept(this)
          addPendingEdge(stmt, myHead)
        case _ =>
      }
    }
  }

  override def visitReturnStatement(ret: ScReturnStmt) {
    val isNodeNeeded = myHead == null || (myHead.element match {
      case Some(e) => e != ret
      case None => false
    })
    ret.expr match {
      case Some(e) => e.accept(this)
      case None =>
    }
    if (isNodeNeeded) startNode(Some(ret)) {rs =>
      addPendingEdge(null, myHead)
    }
    else addPendingEdge(null, myHead)

    // add edge to finally block
    getClosestFinallyInfo.map {finfo => myTransitionInstructions += ((myHead, finfo))}
    interruptFlow()
  }

  override def visitFunctionExpression(stmt: ScFunctionExpr) { /* Do not visit closures */ }

  override def visitTypeDefintion(typedef: ScTypeDefinition) { /* Do not visit inner classes either */ }

  override def visitBlockExpression(block: ScBlockExpr) {
    if (block.isAnonymousFunction) {
      // Do not visit closures
    } else {
      super.visitBlockExpression(block)
    }
  }

  override def visitFunction(fun: ScFunction) { /* Yep, do not visit functions as well :) */ }

  override def visitThrowExpression(throwStmt: ScThrowStmt) {
    val isNodeNeeded = myHead == null || (myHead.element match {
      case Some(e) => e != throwStmt
      case None => false
    })
    throwStmt.body.map(_.accept(this))
    if (isNodeNeeded) startNode(Some(throwStmt)) {rs =>
      addPendingEdge(null, myHead)
    }
    else addPendingEdge(null, myHead)
    interruptFlow()
  }

  private def getClosestFinallyInfo = myCatchedExnStack.find(_.isInstanceOf[FinallyInfo]).asInstanceOf[Option[FinallyInfo]]

  sealed abstract class HandleInfo(val elem: ScalaPsiElement)
  case class CatchInfo(cc: ScCaseClause) extends HandleInfo(cc)
  case class FinallyInfo(fb: ScFinallyBlock) extends HandleInfo(fb)

  override def visitTryExpression(tryStmt: ScTryStmt) {
    val handledExnTypes = tryStmt.catchBlock match {
      case None => Nil
      case Some(cb) => cb.expression match {
        case Some(b: ScBlockExpr) if b.isAnonymousFunction =>
          for (t <- b.caseClauses.toSeq.flatMap(_.caseClauses)) yield CatchInfo(t)
        case None => Nil
      }
    }
    myCatchedExnStack pushAll handledExnTypes
    var catchedExnCount = handledExnTypes.size

    val fBlock = tryStmt.finallyBlock match {
      case None => null
      case Some(x) => x
    }
    if (fBlock != null) {
      myCatchedExnStack push FinallyInfo(fBlock)
      catchedExnCount += 1
    }

    startNode(Some(tryStmt)) {instr =>
      checkPendingEdges(instr)
      // process try block
      val tb = tryStmt.tryBlock
      if (tb != null) {
        tb.accept(this)
      }

      // remove exceptions
      for (_ <- 1 to catchedExnCount) {myCatchedExnStack.pop()}

      val headAfterTry = myHead
      def processCatch(fin: InstructionImpl) = tryStmt.catchBlock.map {cb =>
        cb.expression match {
          case Some(b: ScBlockExpr) if b.isAnonymousFunction =>
            for (cc <- b.caseClauses.toSeq.flatMap(_.caseClauses)) {
              myHead = headAfterTry
              cc.accept(this)
              if (fin == null) {
                addPendingEdge(tryStmt, myHead)
              } else {
                addEdge(myHead, fin)
              }
              myHead = null
            }
          case _ =>
            for (cc <- cb.expression) {
              myHead = headAfterTry
              cc.accept(this)
              if (fin == null) {
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