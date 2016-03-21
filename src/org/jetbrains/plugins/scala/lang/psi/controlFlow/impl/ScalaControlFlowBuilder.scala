package org.jetbrains.plugins.scala
package lang.psi.controlFlow.impl

import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{Instruction, ScControlFlowPolicy}
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
    if (!myPending.contains((instruction, scopeWhenAdded)))
      myPending.insert(math.max(index, 0), (instruction, scopeWhenAdded))
  }

  private def advancePendingEdges(fromScope: ScalaPsiElement, toScope: ScalaPsiElement) {
    for {
      ((instr, scope), idx) <- myPending.zipWithIndex
      if scope != null && PsiTreeUtil.isAncestor(fromScope, scope, false)
    } {
      myPending.update(idx, (instr, toScope))
    }
  }

  private def interruptFlow() {
    myHead = null
  }

  private def moveHead(instr: InstructionImpl) {
    myHead = instr
  }

  /**************************************
   * VISITOR METHODS
   **************************************/

  override def visitPatternDefinition(pattern: ScPatternDefinition) {
    pattern.expr.foreach(_.accept(this))
    for (b <- pattern.bindings if policy.isElementAccepted(b)) {
      val instr = new DefinitionInstruction(inc, b, DefinitionType.VAL)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitVariableDefinition(variable: ScVariableDefinition) {
    variable.expr.foreach(_.accept(this))
    for (b <- variable.bindings if policy.isElementAccepted(b)) {
      val instr = new DefinitionInstruction(inc, b, DefinitionType.VAR)
      checkPendingEdges(instr)
      addNode(instr)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) {
    ref.qualifier match {
      case None =>
        val instr = new ReadWriteVariableInstruction(inc, ref, policy.usedVariable(ref), ScalaPsiUtil.isLValue(ref))
        addNode(instr)
        checkPendingEdges(instr)
      case Some(qual) => qual.accept(this)
    }
  }

  override def visitAssignmentStatement(stmt: ScAssignStmt) {
    stmt.getRExpression match {
      case Some(rv) =>
        rv.accept(this)
        stmt.getParent match {
          case _: ScArgumentExprList =>
          case _ =>
            val lValue = stmt.getLExpression
            lValue.accept(this)
        }
      case _ =>
    }
  }

  override def visitDoStatement(stmt: ScDoStmt) {
    startNode(Some(stmt)) {doStmtInstr =>
      checkPendingEdges(doStmtInstr)
      stmt.getExprBody map {e =>
        e.accept(this)
      }
      stmt.condition map {c =>
        c.accept(this)
        if (myHead != null) {
          checkPendingEdges(myHead)
          addEdge(myHead, doStmtInstr)
          addPendingEdge(stmt, myHead)
        }
      }
    }
  }

  override def visitCaseClause(cc: ScCaseClause) {
    cc.pattern match {
      case Some(p) => for (b <- p.bindings if policy.isElementAccepted(b)) {
        val instr = new DefinitionInstruction(inc, b, DefinitionType.VAL)
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
    startNode(Some(ms)) {msInstr =>
      checkPendingEdges(msInstr)
      ms.expr match {
        case Some(e) => e.accept(this)
        case _ =>
      }
      for (cc <- ms.caseClauses) {
        cc.accept(this)
        advancePendingEdges(cc, ms)
        addPendingEdge(ms, myHead)
        moveHead(msInstr)
      }
    }
  }

  override def visitWhileStatement(ws: ScWhileStmt) {
    startNode(Some(ws)) {instr =>
      checkPendingEdges(instr)
      // for breaks
      //addPendingEdge(ws, myHead)
      ws.condition.foreach(_.accept(this))
      ws.body.foreach {b =>
        b.accept(this)
      }
      checkPendingEdges(instr)
      // add backward edge
      if (myHead != null) addEdge(myHead, instr)
      moveHead(instr)
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall) {
    import call.typeSystem
    val matchedParams = call.matchedParameters
    def isByNameOrFunction(arg: ScExpression) = {
      val param = matchedParams.toMap.get(arg)
      param.isEmpty || param.exists(_.isByName) || param.exists(p => ScFunctionType.isFunctionType(p.paramType))
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
        val instr = new DefinitionInstruction(inc, b, DefinitionType.VAL)
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

  override def visitForExpression(forStmt: ScForStatement) {
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

  override def visitIfStatement(stmt: ScIfStmt) {
    startNode(Some(stmt)) {ifStmtInstr =>
      checkPendingEdges(ifStmtInstr)
      stmt.condition match {
        case Some(cond) =>
          cond.accept(this)
        case None =>
      }

      val head = Option(myHead).getOrElse(ifStmtInstr)

      // reduced if-then expression (without `else`)
      stmt.elseBranch match {
        case None => addPendingEdge(stmt, head)
        case _ =>
      }

      stmt.thenBranch match {
        case Some(tb) =>
          tb.accept(this)
          advancePendingEdges(tb, stmt)
          addPendingEdge(stmt, myHead)
          myHead = head
        case None =>
      }
      stmt.elseBranch match {
        case Some(eb) =>
          eb.accept(this)
          advancePendingEdges(eb, stmt)
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

  override def visitFunctionExpression(stmt: ScFunctionExpr) {
    if (policy == ExtractMethodControlFlowPolicy) addFreeVariables(stmt)
  }

  override def visitTypeDefinition(typedef: ScTypeDefinition) { /* Do not visit inner classes either */ }

  override def visitBlockExpression(block: ScBlockExpr) {
    if (block.isAnonymousFunction) {
      // Do not visit closures
    } else super.visitBlockExpression(block)
  }


  override def visitInfixExpression(infix: ScInfixExpr): Unit = {
    val matchedParams = infix.matchedParameters
    val byNameParam = matchedParams.exists(_._2.isByName)
    if (byNameParam) {
      startNode(Some(infix)) {
        infixInstr =>
          checkPendingEdges(infixInstr)
          addPendingEdge(infix, infixInstr)
          infix.getBaseExpr.accept(this)
          infix.operation.accept(this)
          infix.getArgExpr.accept(this)
          if (myHead == null) moveHead(infixInstr)
      }
    } else {
      infix.getBaseExpr.accept(this)
      infix.operation.accept(this)
      infix.getArgExpr.accept(this)
    }
  }

  override def visitFunction(fun: ScFunction) {
    if (policy != ExtractMethodControlFlowPolicy) return

    if (policy.isElementAccepted(fun)) {
      val instr = new DefinitionInstruction(inc, fun, DefinitionType.DEF)
      checkPendingEdges(instr)
      addNode(instr)
    }
    addFreeVariables(fun)
  }

  private def addFreeVariables(paramOwner: ScalaPsiElement) {
    val parameters = paramOwner match {
      case owner: ScParameterOwner => owner.parameters
      case ScFunctionExpr(params, _) => params
      case _ => return
    }
    val collectedRefs = ArrayBuffer[ScReferenceExpression]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        if (ref.qualifier.nonEmpty) return

        ref.resolve() match {
          case p: ScParameter if parameters.contains(p) =>
          case named: PsiNamedElement if !PsiTreeUtil.isAncestor(paramOwner, named, false) && policy.isElementAccepted(named) =>
            collectedRefs += ref
          case _ =>
        }
      }
    }

    paramOwner.accept(visitor)

    for (ref <- collectedRefs) {
      val instr = new ReadWriteVariableInstruction(inc, ref, policy.usedVariable(ref), ScalaPsiUtil.isLValue(ref))
      addNode(instr)
      checkPendingEdges(instr)
    }
  }

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

  private def getClosestFinallyInfo = myCatchedExnStack.collectFirst {case fi: FinallyInfo => fi}

  sealed abstract class HandleInfo(val elem: ScalaPsiElement)
  case class CatchInfo(cc: ScCaseClause) extends HandleInfo(cc)
  case class FinallyInfo(fb: ScFinallyBlock) extends HandleInfo(fb)

  override def visitTryExpression(tryStmt: ScTryStmt) {
    val handledExnTypes = tryStmt.catchBlock match {
      case None => Nil
      case Some(cb) => cb.expression match {
        case Some(b: ScBlockExpr) if b.hasCaseClauses =>
          for (t <- b.caseClauses.toSeq.flatMap(_.caseClauses)) yield CatchInfo(t)
        case _ => Nil
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

    startNode(Some(tryStmt)) {tryStmtInstr =>
      checkPendingEdges(tryStmtInstr)
      // process try block
      val tb = tryStmt.tryBlock
      if (tb != null) {
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
      for (_ <- 1 to catchedExnCount) {myCatchedExnStack.pop()}

      def processCatch(fin: InstructionImpl) = tryStmt.catchBlock.map {cb =>
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
        processCatch((null))
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