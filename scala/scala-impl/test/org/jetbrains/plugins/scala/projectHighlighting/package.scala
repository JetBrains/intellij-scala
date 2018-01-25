package org.jetbrains.plugins.scala

import com.intellij.openapi.util.TextRange

package object projectHighlighting {
  implicit def toTextRange(pair: (Int, Int)): TextRange = new TextRange(pair._1, pair._2)
}
