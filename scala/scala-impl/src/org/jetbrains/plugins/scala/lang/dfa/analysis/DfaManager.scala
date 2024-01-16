package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaDfaListener, ScalaDfaResult}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

object DfaManager {
  private val log = Logger.getInstance(getClass)
  private val cachedDfaResults =
    cached("DfaManager.dfaCaches", ModTracker.anyScalaPsiChange, () => new ConcurrentHashMap[ScFunctionDefinition, Future[Option[ScalaDfaResult]]]())

  def getDfaResultFor(fun: ScFunctionDefinition): Future[Option[ScalaDfaResult]] = {
    val cache = cachedDfaResults()
    val promise = Promise[Option[ScalaDfaResult]]()

    Option(cache.putIfAbsent(fun, promise.future))
      .map { x =>
        println(s"Already inserted (${fun.name}), completed: ${x.isCompleted}")
        x
      }
      .getOrElse {
        try {
          println(s"Inserting (${fun.name})")
          val result = computeDfaResultFor(fun, buildUnsupportedPsiElements = true)
          promise.success(result)
        } catch {
          case e: ProcessCanceledException =>
            println(s"Process canceled (${fun.name})")
            cache.remove(fun)
            promise.failure(e)
            throw e
          case NonFatal(e) =>
            promise.success(None)
            log.error(e)
          case e: Throwable =>
            promise.failure(e)
            throw e
        }
        promise.future
      }
  }

  def computeDfaResultFor(fun: ScFunctionDefinition, buildUnsupportedPsiElements: Boolean): Option[ScalaDfaResult] = fun.body.flatMap { body =>
    val factory = new DfaValueFactory(fun.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))

    val analysedMethodInfo = AnalysedMethodInfo(fun, 1)
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, factory, body, buildUnsupportedPsiElements)
    controlFlowBuilder.transformStatement(body, ResultReq.None)
    val flow = controlFlowBuilder.build()
    try {
      val listener = new ScalaDfaListener
      val interpreter = new StandardDataFlowInterpreter(flow, listener)
      if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
        Some(listener.result)
      } else {
        None
      }
    } catch {
      case NonFatal(e) =>
        throw ScalaDfaException(fun, flow.toString, e)
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }

  case class ScalaDfaException(fun: ScFunctionDefinition, cfg: String, cause: Throwable)
    extends Exception(s"Failed to analyze function ${fun.name}, cfg:\n$cfg", cause)
}
