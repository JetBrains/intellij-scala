package org.jetbrains.jps.incremental.scala

/**
 * Corresponds to [[org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode]], but avoids linking against JPS code.
 */
sealed trait ExitCode

object ExitCode {
  case object NothingDone extends ExitCode
  case object Ok extends ExitCode
  case object Abort extends ExitCode
  case object AdditionalPassRequired extends ExitCode
  case object ChunkRebuildRequired extends ExitCode
}
