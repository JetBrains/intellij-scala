package org.jetbrains.plugins.scala.compiler.highlighting.core

/**
 * An enum whose values correspond to [[com.intellij.codeInsight.daemon.impl.HighlightInfoType]].
 * We use it to avoid early initialisation of the UI in tests, to keep the logic
 * unit-testable, without having to spin up the whole IDE in tests.
 */
private[highlighting] enum HighlightInfoType:
  case WrongRef
  case Error
  case UnusedSymbol
  case Warning
  case WeakWarning
  case Information
