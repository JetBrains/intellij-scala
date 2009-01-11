package org.jetbrains.plugins.scala.lang.parser.parsing.top

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import base.AccessModifier
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes
import params.ClassParamClauses

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
      case _ => {
        builder error ErrMsg("identifier.expected")
        return false
      }
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
      case _ => builder.mark.done(ScalaElementTypes.PARAM_CLAUSES)
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