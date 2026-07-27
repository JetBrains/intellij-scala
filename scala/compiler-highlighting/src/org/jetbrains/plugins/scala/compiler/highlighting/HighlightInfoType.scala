package org.jetbrains.plugins.scala.compiler.highlighting

/**
 * An enum whose values correspond to [[com.intellij.codeInsight.daemon.impl.HighlightInfoType]].
 * We use it to avoid early initialisation of the UI in tests, to keep the logic
 * unit-testable, without having to spin up the whole IDE in tests.
 */
private sealed trait HighlightInfoType extends Product with Serializable

private object HighlightInfoType {
  case object WrongRef extends HighlightInfoType
  case object Error extends HighlightInfoType
  case object UnusedSymbol extends HighlightInfoType
  case object Warning extends HighlightInfoType
  case object WeakWarning extends HighlightInfoType
  case object Information extends HighlightInfoType
}
