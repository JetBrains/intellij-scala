package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

class ScalaTokenType(val debugName: String) extends IElementType(debugName, ScalaLanguage.INSTANCE) {

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

  val FunctionalArrow = new ScalaTokenType("=>>")

  /* soft keywords */
  val Derives = new ScalaTokenType("derives")
  val As = new ScalaTokenType("as")

  // TODO to be removed
  object IsEnum {

    def unapply(elementType: IElementType)
               (implicit builder: ScalaPsiBuilder): Boolean = elementType match {
      case ScalaTokenTypes.tIDENTIFIER => builder.getTokenText == Enum.debugName
      case _ => false
    }
  }
}