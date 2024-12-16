package org.jetbrains.plugins.scala.inferAst

import com.github.sbt.junit.jupiter.internal.TestLogger
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.{DfaCondition, DfaValueFactory}
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestLoggerKt
import com.jetbrains.rd.util.threading.CompoundThrowable
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.collection.mutable

case class AnalysisItem(method: ScFunctionDefinition, obj: ScObject, args: Seq[Any]) {
  override def toString: String = s"AnalysisItem(${method.name} in ${obj.name}, [${args.mkString(", ")}])"
}

case class AnalysisResult(trueResult: AstAutomaton[AstAction],
                          falseResult: Option[AstAutomaton[AstAction]])

private class GlobalAnalysis(project: Project) {
  private val valueFactory = new DfaValueFactory(project)
  private val addedItems = mutable.Set.empty[AnalysisItem]
  private val missingItems = mutable.Queue.empty[AnalysisItem]
  private val doneItems = mutable.Map.empty[AnalysisItem, AnalysisResult]
  private val cfgs = mutable.Map.empty[ScFunctionDefinition, ControlFlow]

  val exceptions: mutable.Buffer[Throwable] = mutable.Buffer.empty

  def resultItems: Map[AnalysisItem, AnalysisResult] = doneItems.toMap

  def addToAnalysis(item: AnalysisItem): Unit = {
    if (addedItems.add(item)) {
      missingItems.addOne(item)
    }
  }

  def run(): Unit = {
    var success = 0
    while (missingItems.nonEmpty) {
      val item = missingItems.dequeue()
      //try {
        analyze(item)
        val errors = TestLoggerKt.getErrorLog.takeLoggedErrors()
        if (!errors.isEmpty) {
          throw new CompoundThrowable(errors)
        }
        success += 1
      //} catch {
      //  case e: Throwable =>
      //    exceptions += e
      //}
    }

    println(s"Successes: $success")
    println(s"Failures:  ${exceptions.length}")
    println(exceptions.groupBy(_.getClass.getName).map{ case (name, exps) => s"$name: ${exps.length}"}.mkString("\n"))
  }

  def getCfg(fun: ScFunctionDefinition): ControlFlow =
    cfgs.getOrElseUpdate(fun, {
      val body = fun.body.get
      val analysedMethodInfo = AnalysedMethodInfo(fun, 1)
      val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, valueFactory, body, buildUnsupportedPsiElements = false)

      controlFlowBuilder.transformAndReportStatement(body)
      controlFlowBuilder.build()
    })

  def analyze(item: AnalysisItem): Unit = {
    if (doneItems.contains(item)) {
      return
    }
    println("Analyzing " + item)
    val cfg = getCfg(item.method)
    println(cfg)

    val interpreter = new InferAstDfaInterpreter(cfg, item.obj)
    val initialState = InferAstDfaMemoryState(valueFactory)
    interpreter.interpret(initialState)

    val resultStates = interpreter.resultingStates

    val result =
      if (item.method.returnType.exists(_.isBoolean)) {
        val trueAutomatons = mutable.Buffer.empty[AstAutomaton[AstAction]]
        val falseAutomatons = mutable.Buffer.empty[AstAutomaton[AstAction]]
        resultStates.foreach { state =>
          val automaton = state.buildAstAutomaton()

          automaton.reachableNodes.map(_.action).foreach {
            case AstAction.Call(item, _) => addToAnalysis(item)
            case _ =>
          }

          val result = state.peek().eq(DfTypes.TRUE)
          if (result != DfaCondition.getFalse) {
            trueAutomatons += automaton
          }
          if (result != DfaCondition.getTrue) {
            falseAutomatons += automaton
          }
        }

        val trueAutomaton = AstAutomaton.squash(trueAutomatons).minimized
        val falseAutomaton = AstAutomaton.squash(falseAutomatons).minimized

        println(s"Result $item (true):")
        println(trueAutomaton.toGraphviz)
        println()

        println(s"Result $item (false):")
        println(falseAutomaton.toGraphviz)
        println()

        AnalysisResult(trueAutomaton, Some(falseAutomaton))
      } else {
        val automaton = AstAutomaton.squash(resultStates.map(_.buildAstAutomaton())).minimized
        println(s"Result $item (non-boolean):")
        println(automaton.toGraphviz)
        println()
        AnalysisResult(automaton, None)
      }

    doneItems.put(item, result)


//    println("result.size: " + result.size)
//    val automaton = AstAutomaton.squash(result.map(_.buildAstAutomaton())).minimized
//    println(automaton.toGraphviz)
//
//    println("----------------")
//    val (main, inners) = ElementAst.from(automaton)
//    println(main.toGraphviz)
//    println("----------------")
//    inners.foreach { case (name, a) =>
//      println(name + ":")
//      println(a.toGraphviz)
//      println("----------------")
//    }


  }
}


//class Analysis(item: AnalysisItem, global: GlobalAnalysis) {
//  val markers = mutable.Map.empty[Pos, Set[AstAction]]
//  val states = mutable.Map.empty[Pos, Set[(State, Pos)]]
//
//  def addMarkerInfo(pos: Pos, node: AstAction): Unit =
//    markers.updateWith(pos) {
//      case Some(prev) => Some(prev + node)
//      case None => Some(Set(node))
//    }
//
//  def analyze(): Unit = {
//    val method = item.method
//
//    val cfg = global.getCfg(method)
//
//    val queue = mutable.Queue.empty[Pos]
//    val inQueue = mutable.Map.empty[Pos]
//    queue.enqueue((None, Pos(item.method, 0), State.from(item)))
//
//    val incomingStates = mutable.Map.empty[Pos, Set[State]]
//
//    def addIncoming(pos: Pos, next: State): Unit = {
//
//    }
//
//    while (queue.nonEmpty) {
//      val (pos, incoming) = queue.dequeue()
//
//      // handle merging
//      val allIncoming =
//        incomingStates.updateWith(pos) {
//          case None => Some(incoming)
//          case Some(prev) => Some(prev ++ incoming)
//        }
//        .get
//
//      val outgoing = allIncoming.flatMap(processState(_, pos, global.getCfg(pos.fun).getInstruction(pos.idx)))
//      //println(stateAfter)
//      if (!states.get(pos).contains(outgoing)) {
//        states.put(pos, outgoing)
//        outgoing.foreach { case (state, target)} => queue.enqueue((Some(pos), next, stateAfter)))
//      }
//    }
//  }
//
//  private def processState(state: State, pos: Pos, instr: Instruction): Set[(State, Pos)] = {
//    implicit class StateExt(private val state: State) extends AnyVal {
//      def toNextPos: Set[(State, Pos)] = Set(state -> pos.next)
//    }
//    def ignore = Set((state, pos.next))
//    instr match {
//      case _: PopInstruction =>
//        state
//          .withPop
//          .toNextPos
//
//      case instr: PushValueInstruction =>
//        state
//          .withPushUnknown(pos)
//          .toNextPos
//
//      case instr: PushInstruction =>
//        val value = state.variables.getOrElse(instr.getValue, StackValue.Val(instr.getValue))
//        state
//          .withPush(value)
//          .toNextPos
//
//      case instr: AssignInstruction =>
//        state
//          .withPop
//          .withAssign(instr.getAssignedValue, state.stackTop)
//          .withPushUnknown(pos)
//          .toNextPos
//
//      case instr: SimpleAssignmentInstruction =>
//        state
//          .withPop
//          .withAssign(instr.getDestination, state.stackTop)
//          .withPushUnknown(pos)
//          .toNextPos
//
//      case _: FinishElementInstruction =>
//        ignore
//
//      case _: ReturnInstruction =>
//        Set.empty
//
//      case instr: ScalaInvocationInstruction =>
//        val invoked = instr.invocationInfo.invokedElement.getOrElse(throw new Exception("Cannot process call to unknown method"))
//        val effect = processKnownFunction.getOrElse(invoked.toString, throw new Exception(s"Cannot process call to $invoked"))
//        effect(state, pos, instr)
//          .toNextPos
//
//      case instr =>
//        println()
//        println(global.getCfg(pos.fun))
//        println()
//        println(s"Failed to process instruction: $instr (${instr.getClass})")
//        println(s"Stack: ${state.stack.mkString(", ")}")
//        println(s"Vars:")
//        state.variables.foreach { case (v, value) => s"- $v = $value"}
//        ???
//    }
//  }
//
//  private val processKnownFunction: Map[String, (State, Pos, Instruction) => State] = Map(
//    (
//      "PsiBuilder#mark",
//      (state, pos, instr) => {
//        val mark = AstAction.Mark(pos)
//        addMarkerInfo(pos, mark)
//        state.withPop.withPush(StackValue.Marker(mark))
//      }
//    ),
//    (
//      "Marker#done",
//      (state, pos, instr) => {
//        val StackValue.Val(ty) = state.stackTop
//        val StackValue.Marker(mark) = state.stack(1)
//        addMarkerInfo(pos, AstAction.Done(mark, ty.toString))
//        state.withPop.withPop.withPush(StackValue.Unknown(pos))
//      }
//    ),
//    (
//      "SyntaxTreeBuilder#advanceLexer",
//      (state, pos, instr) => {
//        addMarkerInfo(pos, AstAction.Token)
//        state.withPop.withPush(StackValue.Unknown(pos))
//      }
//    )
//  )
//
//  def astAutomaton: Set[AstNode] =
//    ???
//}
//
//
//case class Pos(fun: ScFunctionDefinition, idx: Int) {
//  def next: Pos = Pos(fun, idx + 1)
//}
//
//final case class State(stack: List[StackValue], variables: Map[DfaValue, StackValue]) {
//  def stackTop: StackValue = stack.head
//
//  def withPop: State = {
//    assert(stack.nonEmpty)
//    copy(stack = stack.tail)
//  }
//
//  def withPush(value: StackValue): State =
//    copy(stack = value :: stack)
//
//  def withPushUnknown(pos: Pos): State = withPush(StackValue.Unknown(pos))
//
//  def withAssign(variable: DfaValue, value: StackValue): State =
//    copy(variables = variables + (variable -> value))
//}
//
//object State {
//  def from(item: AnalysisItem): State = State(stack = Nil, variables = Map.empty)
//  def merge(states: Iterator[State]): State = ???
//}
//
//
//sealed abstract class StackValue
//object StackValue {
//  case class Unknown(pos: Pos) extends StackValue
//  case class Val(value: DfaValue) extends StackValue
//  case class Marker(mark: AstAction.Mark) extends StackValue
//}
//
//
//sealed abstract class AstAction
//object AstAction {
//  final case class Mark(pos: Pos) extends AstAction
//  final case class Done(mark: Mark, elementType: String) extends AstAction
//  final case object Token extends AstAction
//}
//
//
//class AstNode(val action: AstAction) {
//  val nexts = mutable.Set.empty[AstNode]
//
//  def ~> (next: AstNode): AstNode = {
//    nexts.addOne(next)
//    next
//  }
//}