package org.jetbrains.plugins.scala.inferAst

import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, FinishElementInstruction, Instruction, PopInstruction, PushInstruction, PushValueInstruction, ReturnInstruction, SimpleAssignmentInstruction}
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.ScalaInvocationInstruction
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.collection.mutable

case class AnalysisItem(method: ScFunctionDefinition)

private class GlobalAnalysis(project: Project) {
  private val factory = new DfaValueFactory(project)
  private val addedItems = mutable.Set.empty[AnalysisItem]
  private val missingItems = mutable.Queue.empty[AnalysisItem]
  private val doneItems = mutable.Map.empty[AnalysisItem, Unit]
  private val cfgs = mutable.Map.empty[ScFunctionDefinition, ControlFlow]

  def addToAnalysis(m: ScFunctionDefinition): Unit =
    addToAnalysis(AnalysisItem(m))

  def addToAnalysis(item: AnalysisItem): Unit = {
    if (addedItems.add(item)) {
      missingItems.addOne(item)
    }
  }

  def run(): Unit = {
    while (missingItems.nonEmpty) {
      val item = missingItems.dequeue()
      analyze(item)
    }
  }

  def getCfg(fun: ScFunctionDefinition): ControlFlow =
    cfgs.getOrElseUpdate(fun, {
      val body = fun.body.get
      val analysedMethodInfo = AnalysedMethodInfo(fun, 1)
      val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, factory, body, buildUnsupportedPsiElements = false)

      controlFlowBuilder.transformStatement(body, ResultReq.None)
      controlFlowBuilder.build()
    })

  def analyze(item: AnalysisItem): Unit = {
    val analysis = new Analysis(item, this)
    analysis.analyze()
  }
}


class Analysis(item: AnalysisItem, global: GlobalAnalysis) {
  val markers = mutable.Map.empty[Pos, Set[AstAction]]
  val states = mutable.Map.empty[Pos, Set[(State, Pos)]]

  def addMarkerInfo(pos: Pos, node: AstAction): Unit =
    markers.updateWith(pos) {
      case Some(prev) => Some(prev + node)
      case None => Some(Set(node))
    }

  def analyze(): Unit = {
    val method = item.method

    val cfg = global.getCfg(method)

    val queue = mutable.Queue.empty[Pos]
    val inQueue = mutable.Map.empty[Pos]
    queue.enqueue((None, Pos(item.method, 0), State.from(item)))

    val incomingStates = mutable.Map.empty[Pos, Set[State]]

    def addIncoming(pos: Pos, next: State): Unit = {

    }

    while (queue.nonEmpty) {
      val (pos, incoming) = queue.dequeue()

      // handle merging
      val allIncoming =
        incomingStates.updateWith(pos) {
          case None => Some(incoming)
          case Some(prev) => Some(prev ++ incoming)
        }
        .get

      val outgoing = allIncoming.flatMap(processState(_, pos, global.getCfg(pos.fun).getInstruction(pos.idx)))
      //println(stateAfter)
      if (!states.get(pos).contains(outgoing)) {
        states.put(pos, outgoing)
        outgoing.foreach { case (state, target)} => queue.enqueue((Some(pos), next, stateAfter)))
      }
    }
  }

  private def processState(state: State, pos: Pos, instr: Instruction): Set[(State, Pos)] = {
    implicit class StateExt(private val state: State) extends AnyVal {
      def toNextPos: Set[(State, Pos)] = Set(state -> pos.next)
    }
    def ignore = Set((state, pos.next))
    instr match {
      case _: PopInstruction =>
        state
          .withPop
          .toNextPos

      case instr: PushValueInstruction =>
        state
          .withPushUnknown(pos)
          .toNextPos

      case instr: PushInstruction =>
        val value = state.variables.getOrElse(instr.getValue, StackValue.Val(instr.getValue))
        state
          .withPush(value)
          .toNextPos

      case instr: AssignInstruction =>
        state
          .withPop
          .withAssign(instr.getAssignedValue, state.stackTop)
          .withPushUnknown(pos)
          .toNextPos

      case instr: SimpleAssignmentInstruction =>
        state
          .withPop
          .withAssign(instr.getDestination, state.stackTop)
          .withPushUnknown(pos)
          .toNextPos

      case _: FinishElementInstruction =>
        ignore

      case _: ReturnInstruction =>
        Set.empty

      case instr: ScalaInvocationInstruction =>
        val invoked = instr.invocationInfo.invokedElement.getOrElse(throw new Exception("Cannot process call to unknown method"))
        val effect = processKnownFunction.getOrElse(invoked.toString, throw new Exception(s"Cannot process call to $invoked"))
        effect(state, pos, instr)
          .toNextPos

      case instr =>
        println()
        println(global.getCfg(pos.fun))
        println()
        println(s"Failed to process instruction: $instr (${instr.getClass})")
        println(s"Stack: ${state.stack.mkString(", ")}")
        println(s"Vars:")
        state.variables.foreach { case (v, value) => s"- $v = $value"}
        ???
    }
  }

  private val processKnownFunction: Map[String, (State, Pos, Instruction) => State] = Map(
    (
      "PsiBuilder#mark",
      (state, pos, instr) => {
        val mark = AstAction.Mark(pos)
        addMarkerInfo(pos, mark)
        state.withPop.withPush(StackValue.Marker(mark))
      }
    ),
    (
      "Marker#done",
      (state, pos, instr) => {
        val StackValue.Val(ty) = state.stackTop
        val StackValue.Marker(mark) = state.stack(1)
        addMarkerInfo(pos, AstAction.Done(mark, ty.toString))
        state.withPop.withPop.withPush(StackValue.Unknown(pos))
      }
    ),
    (
      "SyntaxTreeBuilder#advanceLexer",
      (state, pos, instr) => {
        addMarkerInfo(pos, AstAction.Token)
        state.withPop.withPush(StackValue.Unknown(pos))
      }
    )
  )

  def astAutomaton: Set[AstNode] =
    ???
}


case class Pos(fun: ScFunctionDefinition, idx: Int) {
  def next: Pos = Pos(fun, idx + 1)
}

final case class State(stack: List[StackValue], variables: Map[DfaValue, StackValue]) {
  def stackTop: StackValue = stack.head

  def withPop: State = {
    assert(stack.nonEmpty)
    copy(stack = stack.tail)
  }

  def withPush(value: StackValue): State =
    copy(stack = value :: stack)

  def withPushUnknown(pos: Pos): State = withPush(StackValue.Unknown(pos))

  def withAssign(variable: DfaValue, value: StackValue): State =
    copy(variables = variables + (variable -> value))
}

object State {
  def from(item: AnalysisItem): State = State(stack = Nil, variables = Map.empty)
  def merge(states: Iterator[State]): State = ???
}


sealed abstract class StackValue
object StackValue {
  case class Unknown(pos: Pos) extends StackValue
  case class Val(value: DfaValue) extends StackValue
  case class Marker(mark: AstAction.Mark) extends StackValue
}


sealed abstract class AstAction
object AstAction {
  final case class Mark(pos: Pos) extends AstAction
  final case class Done(mark: Mark, elementType: String) extends AstAction
  final case object Token extends AstAction
}


class AstNode(val action: AstAction) {
  val nexts = mutable.Set.empty[AstNode]

  def ~> (next: AstNode): AstNode = {
    nexts.addOne(next)
    next
  }
}