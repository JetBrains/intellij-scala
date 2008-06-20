package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import com.intellij.psi._
import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * ValDcl ::= ids ':' Type
 */

object ValDcl {
  def parse(builder: PsiBuilder): Boolean = {
    val returnMarker = builder.mark
    //Look for val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => builder.advanceLexer //Ate val
      case _ => {
        returnMarker.rollbackTo
        return false
      }
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        Ids parse builder
        //Look for :
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            if (Type.parse(builder)) {
              returnMarker.drop
              return true
            }
            else {
              builder error ErrMsg("wrong.type")
              returnMarker.drop
              return true
            }
          }
          case _ => {
            builder error ErrMsg("wrong.val.declaration")
            returnMarker.drop
            return true
          }
        }
      }
      case _ => {
        builder error ErrMsg("identifier.expected")
        returnMarker.drop
        return false
      }
    }
  }
}