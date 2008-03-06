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




import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 11:34:16
* To change this template use File | Settings | File Templates.
*/

/*
  QualId ::= id {. id}
*/

object Qual_Id {
  def parse(builder: PsiBuilder): Boolean = {
    val qualMarker = builder.mark
    return parse(builder,qualMarker)
  }
  def parse(builder: PsiBuilder, qualMarker: PsiBuilder.Marker): Boolean = {
    //parsing first identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer//Ate identifier
        //Look for dot
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            val newMarker = qualMarker.precede
            qualMarker.done(ScalaElementTypes.QUAL_ID)
            builder.advanceLexer//Ate dot
            //recursively parse qualified identifier
            Qual_Id parse (builder,newMarker)
            return true
          }
          case _ => {
            //It's OK, let's close marker
            qualMarker.done(ScalaElementTypes.QUAL_ID)
            return true
          }
        }
      }
      case _ => {
        builder error ScalaBundle.message("wrong.qual.identifier", new Array[Object](0))
        qualMarker.done(ScalaElementTypes.QUAL_ID)
        return true
      }
    }
  }
}