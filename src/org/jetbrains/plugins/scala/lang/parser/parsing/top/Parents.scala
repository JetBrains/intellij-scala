package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
  * @author adkozlov
  */
trait Parents {
  protected val annotType: AnnotType

  protected val elementType: IElementType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!parseParent(builder)) {
      marker.done(elementType)
      return false
    }

    var wrongType = false
    while (builder.getTokenType == ScalaTokenTypes.kWITH && !wrongType) {
      builder.advanceLexer() // Ate with
      if (!annotType.parse(builder, isPattern = false)) {
        builder.error(ErrMsg("wrong.simple.type"))
        wrongType = true
      }
    }
    marker.done(elementType)
    true
  }

  protected def parseParent(builder: ScalaPsiBuilder): Boolean
}
