package org.jetbrains.plugins.scala
package lang
package lexer

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

  val Enum = new ScalaTokenType("enum")
  val Export = new ScalaTokenType("export")
  val Given = new ScalaTokenType("given")
  val Then = new ScalaTokenType("then")

  val As = new ScalaTokenType("as")
  val Derives = new ScalaTokenType("derives")
  val Inline: ScalaModifierTokenType = ScalaTokenTypes.kINLINE
  val Opaque = new ScalaTokenType("opaque")
}