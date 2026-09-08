package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition

/**
 * A position remapped onto the `return` keyword of a conditional (early) return, so that only the keyword is
 * highlighted when the debugger suspends there (SCL-21626).
 *
 * @see org.jetbrains.plugins.scala.debugger.ScalaSourcePositionHighlighter
 */
private final class ScalaConditionalReturnSourcePosition(delegate: SourcePosition)
  extends AbstractScalaSourcePosition(delegate)
