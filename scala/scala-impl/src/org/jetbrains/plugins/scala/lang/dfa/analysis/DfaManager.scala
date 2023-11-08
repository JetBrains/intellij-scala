package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaDfaListener, ScalaDfaResult}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.SeqHasAsJava

object DfaManager {
  private val cachedDfaResults =
    cached("DfaManager.dfaCaches", ModTracker.anyScalaPsiChange, () => new ConcurrentHashMap[ScFunctionDefinition, Option[ScalaDfaResult]]())

  def getDfaResultFor(fun: ScFunctionDefinition): Option[ScalaDfaResult] = {
    val cache = cachedDfaResults()
    Option(cache.get(fun))
      .getOrElse {
        val result = computeDfaResultFor(fun, buildUnsupportedPsiElements = true)
        val alreadyInserted = Option(cache.putIfAbsent(fun, result))
        alreadyInserted.getOrElse(result)
      }
  }

  def computeDfaResultFor(fun: ScFunctionDefinition, buildUnsupportedPsiElements: Boolean): Option[ScalaDfaResult] = fun.body.flatMap { body =>
    val factory = new DfaValueFactory(fun.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))

    val analysedMethodInfo = AnalysedMethodInfo(fun, 1)
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, factory, body, buildUnsupportedPsiElements)
    controlFlowBuilder.transformStatement(body, ResultReq.None)
    val flow = controlFlowBuilder.build()
    val listener = new ScalaDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)
    if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
      Some(listener.result)
    } else {
      None
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }
}
