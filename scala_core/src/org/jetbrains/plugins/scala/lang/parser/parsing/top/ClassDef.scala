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
import org.jetbrains.plugins.scala.lang.parser.parsing.params._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._


import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/** 
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * ClassDef ::= id [TypeParamClause] {Annotation} [AcessModifier] [ClassParamClauses] ClassTemplateOpt
 */

object ClassDef {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => builder.advanceLexer //Ate identifier
      case _ => builder error ErrMsg("identifier.expected")
    }
    //parsing type parameters
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => TypeParamClause parse builder
      case _ => {/*it could be without type parameters*/}
    }
    val constructorMarker = builder.mark
    val annotationsMarker = builder.mark
    var isPrimary = false
    while (Annotation.parse(builder)) {isPrimary = true}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    //parse AccessModifier
    builder.getTokenType match {
      case ScalaTokenTypes.kPRIVATE
         | ScalaTokenTypes.kPROTECTED => {
        AccessModifier parse builder
        isPrimary = true
      }
      case _ => {/*it could be without acces modifier*/}
    }
    //parse class parameters clauses
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        ClassParamClauses parse builder
        isPrimary = true
      }
      case _ => {/*it could be without class parameters clausese*/}
    }
    if (isPrimary) constructorMarker.done(ScalaElementTypes.PRIMARY_CONSTRUCTOR)
    else constructorMarker.rollbackTo
    //parse requires block
    builder.getTokenType match {
      case ScalaTokenTypes.kREQUIRES => Requires parse builder
      case _ => {/*it could be without requires block*/}
    }
    //parse extends block
    ClassTemplateOpt parse builder
    return true
  }
}