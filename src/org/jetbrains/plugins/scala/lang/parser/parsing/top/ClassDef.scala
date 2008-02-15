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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 12:28:40
* To change this template use File | Settings | File Templates.
*/

/*
 * ClassDef ::= id [TypeParamClause] {Annotation} [AcessModifier] [ClassParamClauses] ClassTemplateOpt
 */

object ClassDef {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => builder.advanceLexer //Ate identifier
      case _ => builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
    }
    //parsing type parameters
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => new TypeParamClause[VariantTypeParam](new VariantTypeParam) parse builder
      case _ => {/*it could be without type parameters*/}
    }
    //TODO: parse annotations
    //parse AccessModifier
    builder.getTokenType match {
      case ScalaTokenTypes.kPRIVATE
         | ScalaTokenTypes.kPROTECTED => AccessModifier parse builder
      case _ => {/*it could be without acces modifier*/}
    }
    //parse class parameters clauses
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => ClassParamClauses parse builder
      case _ => {/*it could be without class parameters clausese*/}
    }
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