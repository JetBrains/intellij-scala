package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.TextRange

object TextRangeUtils {

  object ImplicitConversions {

    import scala.language.implicitConversions

    implicit def tupleToTextRange(pair: (Int, Int)): TextRange = new TextRange(pair._1, pair._2)
  }
}
