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



import org.jetbrains.plugins.scala.lang.parser.parsing.params._


import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 11.02.2008
* Time: 19:06:27
* To change this template use File | Settings | File Templates.
*/

/*
 * TypeDcl ::= id [TypeParamClause] ['>;' Type] ['<:' Type]
 */

object TypeDcl {
  def parse(builder: PsiBuilder): Boolean = {
    val returnMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        builder.advanceLexer //Ate def
      }
      case _ => {
        returnMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        builder.advanceLexer //Ate nl
      }
      case _ => {}//nothing
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
        returnMarker.drop
        return true
      }
    }
    var isTypeParamClause = false;
    if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
      isTypeParamClause = TypeParamClause parse builder
    }
    builder.getTokenText match {
      case ">:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
      }
      case _ => {} //nothing
    }
    builder.getTokenText match {
      case "<:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
      }
      case _ => {} //nothing
    }
    returnMarker.drop
    return true
  }
}