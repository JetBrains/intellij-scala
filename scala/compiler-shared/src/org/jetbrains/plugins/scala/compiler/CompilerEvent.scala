package org.jetbrains.plugins.scala.compiler

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.CompilerEventType.CompilerEventType
import org.jetbrains.plugins.scala.util.CompilationId

import java.io.File
import java.util.UUID

sealed trait CompilerEvent {

  def eventType: CompilerEventType

  def compilationId: CompilationId

  def compilationUnitId: Option[CompilationUnitId]
}

object CompilerEvent {

  // can be sent multiple times for different modules by jps compiler
  case class CompilationStarted(compilationId: CompilationId,
                                compilationUnitId: Option[CompilationUnitId])
    extends CompilerEvent {
    
    override def eventType: CompilerEventType = CompilerEventType.CompilationStarted
  }

  case class CompilationPhase(compilationId: CompilationId,
                              compilationUnitId: Option[CompilationUnitId],
                              phase: String)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationPhase
  }

  case class CompilationUnit(compilationId: CompilationId,
                             compilationUnitId: Option[CompilationUnitId],
                             path: String)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationUnit
  }

  case class MessageEmitted(compilationId: CompilationId,
                            compilationUnitId: Option[CompilationUnitId],
                            jpsSessionId: Option[UUID],
                            msg: Client.ClientMsg)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.MessageEmitted
  }

  case class ProgressEmitted(compilationId: CompilationId,
                             compilationUnitId: Option[CompilationUnitId],
                             progress: Double)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.ProgressEmitted
  }
  
  // can be sent multiple times for different modules by jps compiler
  case class CompilationFinished(compilationId: CompilationId,
                                 compilationUnitId: Option[CompilationUnitId],
                                 sources: Set[File])
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationFinished
  }

  final val BuilderId = "compiler-event"
}
