package org.jetbrains.plugins.scala

import com.intellij.openapi.util.TextRange

package object projectHighlighting {
  object ImplicitConversions {
    import scala.language.implicitConversions

    implicit def tupleToTextRange(pair: (Int, Int)): TextRange = new TextRange(pair._1, pair._2)
  }
}
