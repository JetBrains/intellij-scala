package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaTokenType(debugName: String) extends IElementType(debugName, ScalaLanguage.INSTANCE) {

  override def isLeftBound: Boolean = true
}

object ScalaTokenType {

  val Long = new ScalaTokenType("long")
  val Integer = new ScalaTokenType("integer")
  val Double = new ScalaTokenType("double")
  val Float = new ScalaTokenType("float")
}