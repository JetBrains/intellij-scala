package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq.Required.Result
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq.{BuilderContext, None}

sealed abstract class ResultReq {
  type Result

  def result(f: => StackValue)(implicit builderContext: BuilderContext): Result
  def ifResultNeeded(f: => StackValue): Result
  def map(result: Result)(f: StackValue => StackValue): Result
  def flatMap(result: Result)(f: StackValue => Result): Result
  def filter(results: Seq[Result]): Seq[StackValue]
  final def filter(first: Result, rest: Result*): Seq[StackValue] = filter(first +: rest)
  def mapM(results: Seq[Result])(f: Seq[StackValue] => StackValue): Result
  final def mapM(first: Result, rest: Result*)(f: Seq[StackValue] => StackValue): Result = mapM(first +: rest)(f)
}

object ResultReq {
  case object Required extends ResultReq {
    override type Result = StackValue

    override def ifResultNeeded(f: => StackValue): Result = f
    override def result(f: => StackValue)(implicit builderContext: BuilderContext): StackValue = f
    override def map(result: Result)(f: StackValue => StackValue): Result = f(result)
    override def flatMap(result: Result)(f: StackValue => StackValue): Result = f(result)
    override def filter(results: Seq[Result]): Seq[StackValue] = results
    override def mapM(results: Seq[Result])(f: Seq[StackValue] => StackValue): StackValue = f(results)
  }
  case object None extends ResultReq {
    override type Result = Unit
    override def ifResultNeeded(f: => StackValue): Result = ()

    override def result(f: => StackValue)(implicit builderContext: BuilderContext): Unit =
      builderContext.builder.pop(f)

    override def map(result: Result)(f: StackValue => StackValue): Result = ()
    override def flatMap(result: Result)(f: StackValue => Result): Result = ()
    override def filter(results: Seq[Result]): Seq[StackValue] = Seq.empty
    override def mapM(results: Seq[Result])(f: Seq[StackValue] => StackValue): Result = ()
  }

  final class BuilderContext(private[ResultReq] val builder: ScalaDfaControlFlowBuilder) extends AnyVal
}