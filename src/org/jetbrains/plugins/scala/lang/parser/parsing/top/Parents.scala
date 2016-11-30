package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
trait Parents {
  protected def elementType: IElementType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!parseFirstParent(builder)) {
      marker.done(elementType)
      return false
    }

    var wrongType = false
    while (builder.getTokenType == ScalaTokenTypes.kWITH && !wrongType) {
      builder.advanceLexer() // Ate with
      if (!parseParent(builder)) {
        builder.error(ErrMsg("wrong.simple.type"))
        wrongType = true
      }
    }
    marker.done(elementType)
    true
  }

  protected def parseFirstParent(builder: ScalaPsiBuilder): Boolean

  protected def parseParent(builder: ScalaPsiBuilder): Boolean
}
