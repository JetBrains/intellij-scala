package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Expr, ExprInIndentationRegion}
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/*
  TmplDef           ::=  ...
                     |   ‘given’ GivenDef
  GivenDef          ::=  [GivenSig] Type ‘=’ Expr
                     |   [GivenSig] ConstrApps [TemplateBody]
  GivenSig          ::=  [id] [DefTypeParamClause] {UsingParamClause} ‘as’
 */
object GivenDef {
  def parse(templateMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    assert(builder.isScala3)
    assert(builder.getTokenType == ScalaTokenType.GivenKeyword)

    builder.advanceLexer() // ate given

    GivenSig()

    if(parseGivenAlias()) {
      templateMarker.done(ScalaElementType.GIVEN_ALIAS)
    } else if (parseGivenDefinition()) {
      templateMarker.done(ScalaElementType.GivenDefinition)
    } else {
      templateMarker.drop()
    }
  }

  def parseGivenAlias()(implicit builder: ScalaPsiBuilder): Boolean = {
    val aliasMarker = builder.mark()
    if (Type.parse(builder) && builder.getTokenType == ScalaTokenTypes.tASSIGN) {
      // given alias
      aliasMarker.drop()
      builder.advanceLexer() // ate =

      if (!ExprInIndentationRegion()) {
        builder.error(ScalaBundle.message("expression.expected"))
      }

      true
    } else {
      aliasMarker.rollbackTo()
      false
    }
  }

  def parseGivenDefinition()(implicit builder: ScalaPsiBuilder): Boolean = {
    val extendsBlockMarker = builder.mark()
    if (ConstrApps()) {
      TemplateBody()
      extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)
      true
    } else {
      extendsBlockMarker.drop()
      false
    }
  }
}

object GivenSig extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val givenSigMarker = builder.mark()

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
          builder.getTokenText != ScalaTokenType.AsKeyword.text) {
      builder.advanceLexer() // ate id
    }

    TypeParamClause.parse(builder)

    ParamClauses.parse(builder)

    if (builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword)) {
      givenSigMarker.drop()
      true
    } else {
      givenSigMarker.rollbackTo()
      false
    }
  }
}