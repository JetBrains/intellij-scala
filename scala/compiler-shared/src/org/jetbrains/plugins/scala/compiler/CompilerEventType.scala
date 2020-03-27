package org.jetbrains.plugins.scala.compiler

object CompilerEventType
  extends Enumeration {

  type CompilerEventType = Value

  final val MessageEmitted = Value("message-emitted")
  final val RangeMessageEmitted = Value("range-message-emitted")
  final val CompilationFinished = Value("compilation-finished")
  final val ProgressEmitted = Value("progress-emitted")
}
