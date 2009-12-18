package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl


import org.jetbrains.plugins.scala.psi.api.ScalaRecursiveElementVisitor
import collection.mutable.ArrayBuffer
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

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
        val pendingInstructions = myPending.reverse.filter {case (_, e) => e != null && !PsiTreeUtil.isAncestor(e, elem, false)}
        for ((instr, _) <- pendingInstructions) {
          addEdge(instr, instruction)
        }
        myPending.dropRight(pendingInstructions.length)
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
    myPending.insert(index, (instruction, scopeWhenAdded))
  }


  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPattern(pattern: ScPattern) {
    for (b <- pattern.bindings) {
      addNode(new DefineValueInstruction(inc, b))
    }
  }
}