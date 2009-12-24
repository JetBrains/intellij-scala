package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl


import org.jetbrains.plugins.scala.psi.api.ScalaRecursiveElementVisitor
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import scala.collection.mutable.ListBuffer
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern, ScCaseClause}

/**
 * @author ilyas
 */

class ScalaControlFlowBuilder(startInScope: ScalaPsiElement,
                              endInsScope: ScalaPsiElement)
        extends ScalaRecursiveElementVisitor {
  private val myInstructions = new ArrayBuffer[InstructionImpl]
  private val myPending = new ArrayBuffer[(InstructionImpl, ScalaPsiElement)]
  private var myInstructionNum = 0
  private var myHead: InstructionImpl = null


  def buildControlflow(scope: ScalaPsiElement): Seq[Instruction] = {
    // initial node
    startNode(None) {
      instr =>
        scope.accept(this)
    }
    // final node
    emptyNode()
    myInstructions.toSeq
  }

  def inc = {
    val num = myInstructionNum
    myInstructionNum += 1
    num
  }

  override def visitElement(element: ScalaPsiElement) =
    if ((element eq startInScope) || !(element eq endInsScope)) super.visitElement(element)

  def emptyNode(): Unit = startNode(None) {_ =>}

  def startNode(element: Option[ScalaPsiElement])(body: InstructionImpl => Unit): Unit = startNode(element, true)(body)

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
    myInstructions + instr
    if (myHead != null) addEdge(myHead, instr)
    myHead = instr
  }

  private def addEdge(from: InstructionImpl, to: InstructionImpl) {
    if (!from.succ.contains(to)) from.addSucc(to)
    if (!to.pred.contains(from)) to.addPred(from)
  }

  private def checkPendingEdges(instruction: InstructionImpl) {
    instruction.element match {
      case Some(elem) => {
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
      }
      case None => {
        for ((from, _) <- myPending) addEdge(from, instruction)
        myPending.clear
      }
    }
  }

  private def addPendingEdge(scopeWhenAdded: ScalaPsiElement, instruction: InstructionImpl) {
    if (instruction == null) return
    var index = 0
    if (scopeWhenAdded != null) {
      index = myPending.findIndexOf {case (_, e) => !PsiTreeUtil.isAncestor(e, scopeWhenAdded, true)}
    }
    myPending.insert(Math.max(index, 0), (instruction, scopeWhenAdded))
  }

  private def flowInterrupted = myHead = null

  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition) {
    pattern.expr.accept(this)
    for (b <- pattern.bindings) {
      val instr = new DefineValueInstruction(inc, b, false)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition) {
    val rv = variable.expr
    if (rv != null) rv.accept(this)
    for (b <- variable.bindings) {
      val instr = new DefineValueInstruction(inc, b, true)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) = {
    ref.qualifier match {
      case None => {
        val instr = new ReadWriteVariableInstruction(inc, ref, ScalaPsiUtil.isLValue(ref))
        addNode(instr)
        checkPendingEdges(instr)
      }
      case _ =>
    }
  }

  override def visitAssignmentStatement(stmt: ScAssignStmt) {
    val lValue = stmt.getLExpression
    stmt.getRExpression match {
      case Some(rv) => {
        rv.accept(this)
        lValue.accept(this)
      }
      case _ =>
    }
  }

  override def visitDoStatement(stmt: ScDoStmt) = {
    startNode(Some(stmt)) {i =>
      stmt.getExprBody map {e =>
        e.accept(this)
        addPendingEdge(stmt, myHead)
      }
      stmt.condition map { c =>
        c.accept(this)
        if (myHead != null) {
          addEdge(myHead, i)
        }
      }
      flowInterrupted
    }
  }

  override def visitCaseClause(cc: ScCaseClause) = {
    cc.pattern match {
      case Some(p) => for (b <- p.bindings) {
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

  override def visitMatchStatement(ms: ScMatchStmt) = {
    startNode(Some(ms)) {instr =>
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

  override def visitWhileStatement(ws: ScWhileStmt) = {
    startNode(Some(ws)) {instr =>
      checkPendingEdges(instr)
      // for breaks
      addPendingEdge(ws, myHead)
      ws.condition.map(_.accept(this))
      ws.body.map {b =>
        b.accept(this)
      }
      checkPendingEdges(instr)
      // add backward edge
      if (myHead != null) addEdge(myHead, instr)
      flowInterrupted
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall) = {
    for (arg <- call.argumentExpressions) arg.accept(this)
    val receiver = call.getInvokedExpr
    if (receiver != null) {
      receiver.accept(this)
    }
  }

  override def visitGenerator(gen: ScGenerator) = {
    val rv = gen.rvalue
    if (rv != null) rv.accept(this)
    val pat = gen.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitGuard(guard: ScGuard) = guard.expr match {
    case Some(e) => e.accept(this)
    case _ =>
  }

  override def visitPattern(pat: ScPattern) = pat match {
    case b: ScBindingPattern => {
      val instr = new DefineValueInstruction(inc, b, false)
      checkPendingEdges(instr)
      addNode(instr)
    }
    case _ => super.visitPattern(pat)
  }

  override def visitEnumerator(enum: ScEnumerator) = {
    val rv = enum.rvalue
    if (rv != null) rv.accept(this)
    val pat = enum.pattern
    if (pat != null) pat.accept(this)
  }

  override def visitForExpression(expr: ScForStatement) = {
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

  override def visitIfStatement(stmt: ScIfStmt) = {
    startNode(Some(stmt)) { instr =>
        stmt.condition match {
          case Some(cond) => {
            cond.accept(this)
          }
          case None =>
        }

        // reduced if-then expression (without `else`)
        stmt.elseBranch match {
          case None => addPendingEdge(stmt, myHead)
          case _ =>
        }

        val head = myHead
        stmt.thenBranch match {
          case Some(tb) => {
            tb.accept(this)
            // the context will be refined later
            addPendingEdge(stmt, myHead)
          }
          case None =>
        }
        stmt.elseBranch match {
          case Some(eb) => {
            myHead = head
            eb.accept(this)
            addPendingEdge(stmt, myHead)
          }
          case _ =>
        }
    }
  }
}