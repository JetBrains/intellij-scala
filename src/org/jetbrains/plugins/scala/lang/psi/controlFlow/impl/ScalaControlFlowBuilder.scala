package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl


import org.jetbrains.plugins.scala.psi.api.ScalaRecursiveElementVisitor
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScIfStmt, ScReferenceExpression}
import scala.collection.mutable.ListBuffer

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
    startNode(None) {instr =>
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
    if (checkPending) checkPendingInstructions(instr)
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

  private def checkPendingInstructions(instruction: InstructionImpl) {
    instruction.element match {
      case Some(elem) => {
        val ab = new ArrayBuffer[Int]
        for (i <- 0 until myPending.size) {
          val (inst, scope) = myPending(i)
          if (scope != null &&
          !PsiTreeUtil.isAncestor(scope, elem, false)) {
            addEdge(inst, instruction)
            ab += i
          }
        }
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


  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition) {
    super.visitPatternDefinition(pattern)
    for (b <- pattern.bindings) {
      val instr = new DefineValueInstruction(inc, b, false)
      checkPendingInstructions(instr)
      addNode(instr)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition) {
    super.visitVariableDefinition(variable)
    for (b <- variable.bindings) {
      val instr = new DefineValueInstruction(inc, b, true)
      checkPendingInstructions(instr)
      addNode(instr)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) = {
    ref.qualifier match {
      case None => {
        val instr = new ReadWriteVariableInstruction(inc, ref, ScalaPsiUtil.isLValue(ref))
        addNode(instr)
        checkPendingInstructions(instr)
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

  override def visitIfStatement(stmt: ScIfStmt) = {
    startNode(Some(stmt)) {instr =>
      val head = myHead
      stmt.thenBranch match {
        case Some(tb) => {
          stmt.condition match {
            case Some(cond) => {
              cond.accept(this)
            }
            case None =>
          }
          tb.accept(this)
          // the context will be refined later
          addPendingEdge(stmt, myHead)
        }
        case None =>
      }
      myHead = head
      stmt.elseBranch match {
        case Some(eb) => {
          stmt.condition match {
            case Some(c) => c.accept(this)
            case _ =>
          }
          eb.accept(this)
          addPendingEdge(stmt, myHead)
        }
        case _ =>
      }
    }
  }
}