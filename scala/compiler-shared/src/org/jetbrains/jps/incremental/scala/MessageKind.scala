package org.jetbrains.jps.incremental.scala

/**
 * Corresponds to [[org.jetbrains.jps.incremental.messages.BuildMessage.Kind]], but avoids linking against JPS code.
 */
sealed trait MessageKind extends Product with Serializable

object MessageKind {
  case object Error extends MessageKind
  case object Warning extends MessageKind
  case object Info extends MessageKind
  case object Progress extends MessageKind
  case object JpsInfo extends MessageKind
  case object InternalBuilderError extends MessageKind
  case object Other extends MessageKind
}
