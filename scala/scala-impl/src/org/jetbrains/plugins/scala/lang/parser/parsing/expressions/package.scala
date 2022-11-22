package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.CompoundType

package object expressions {
  private[expressions] def completeParamClauses(paramMarker: PsiBuilder.Marker)
                                               (paramClauseMarker: PsiBuilder.Marker = paramMarker.precede): Unit = {
    val paramListMarker = paramClauseMarker.precede
    paramMarker.done(ScalaElementType.PARAM)
    paramClauseMarker.done(ScalaElementType.PARAM_CLAUSE)
    paramListMarker.done(ScalaElementType.PARAM_CLAUSES)
  }

  private[expressions] def parseParam()(implicit builder: ScalaPsiBuilder): PsiBuilder.Marker = {
    val paramMarker = builder.mark()

    builder.advanceLexer() // ate id
    if (ScalaTokenTypes.tCOLON == builder.getTokenType) {
      builder.advanceLexer() // ate `:`
      val pt = builder.mark()
      CompoundType()
      pt.done(ScalaElementType.PARAM_TYPE)
    }

    paramMarker
  }
}
