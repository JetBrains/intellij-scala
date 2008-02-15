package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 17:37:29
* To change this template use File | Settings | File Templates.
*/

/*
 * SimpleType ::= SimpleType TypeArgs
 *              | SimpleType '#' id
 *              | StableId
 *              | Path '.' 'type'
 *              | '(' Types [','] ')'
 */
/*
object SimpleType {
  def parse(builder: PsiBuilder): Boolean = {
    val simpleTypeMarker = builder.mark
    val forFirstMarker = builder.mark
    //try to look at mistake this or id.this
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE => {
                builder error ScalaBundle.message
              }
            }
          }
        }
      }
    }
  }
}     */