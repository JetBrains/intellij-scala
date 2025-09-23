package org.jetbrains.plugins.scala.compiler

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.SerializablePath
import org.jetbrains.plugins.scala.compiler.CompilerEventType.CompilerEventType
import org.jetbrains.plugins.scala.util.CompilationId

import java.util.UUID

@ApiStatus.Internal
sealed trait CompilerEvent extends Product with Serializable {

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
                                 sources: Set[SerializablePath])
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationFinished
  }

  final val BuilderId = "compiler-event"

  /**
   * Invoked when the final stage of compilation highlighting is finished.
   * It can happen in multiple ways:
   *  1. the document compiler invoked for all open editors (in case incremental compilation doesn't produce any errors)
   *  1. the end of the incremental compilation if it's finished with errors
   *
   * NOTE: this event was introduced fast as it was necessary in tests now.
   * I spent little time on coming up with the cleanest solution, so it can be replaced with anything considered better.
   */
  case class CompilationHighlightingFinalStageFinished(
    compilationId: CompilationId,
  ) extends CompilerEvent {
    override def eventType: CompilerEventType = CompilerEventType.CompilationHighlightingFinalStageFinished
    override def compilationUnitId: Option[CompilationUnitId] = None
  }
}
