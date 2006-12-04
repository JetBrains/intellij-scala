package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.base.ModifierWithoutImplicit

/*
  QualId ::= id {‘.’ id}
*/

object Qual_Id  {

  def parse (builder: PsiBuilder) : ScalaElementType = {

    def subParse (currentMarker: PsiBuilder.Marker, msg: String) : ScalaElementType = {
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
          val nextMarker = currentMarker.precede()
          currentMarker.done(ScalaElementTypes.QUAL_ID)
          ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
          subParse(nextMarker , "Identifier expected")
        } else {
          //currentMarker.done(ScalaElementTypes.QUAL_ID)
          currentMarker.drop
          ScalaElementTypes.QUAL_ID
        }
      } else {
        builder.error(msg)
        currentMarker.drop
        //currentMarker.done(ScalaElementTypes.QUAL_ID)
        ScalaElementTypes.WRONGWAY
      }
    }
    var qualMarker = builder.mark()
    subParse(qualMarker, "At least one identifier expected")

  }

}