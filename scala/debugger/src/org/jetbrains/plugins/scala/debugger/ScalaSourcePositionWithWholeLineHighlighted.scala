package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition

private final class ScalaSourcePositionWithWholeLineHighlighted(delegate: SourcePosition)
  extends AbstractScalaSourcePosition(delegate)
