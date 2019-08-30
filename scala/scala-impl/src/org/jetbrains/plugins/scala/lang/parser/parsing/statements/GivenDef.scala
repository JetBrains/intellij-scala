package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{GivenParamClauses, ParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Type, TypeArgs}

/*
 * GivenDef         ::=  [id] [DefTypeParamClause] GivenBody
 * GivenBody        ::=  [‘as’ ConstrApp {‘,’ ConstrApp }] {GivenParamClause} [TemplateBody]
 *                    |  ‘as’ Type {GivenParamClause} ‘=’ Expr
 *                    |  ‘(’ DefParam ‘)’ TemplateBody
 */
object GivenDef extends ParsingRule {
  def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenType._
    import builder._

    if (!GivenKeyword.isCurrentToken(builder)) {
      return false
    }
    val faultMarker = builder.mark()
    //GivenKeyword.remapCurrentToken()
    advanceLexer() // Ate given

    if (!GivenKeyword.isCurrentToken(builder) && !AsKeyword.isCurrentToken(builder)) {
      advanceLexer()
    }

    TypeArgs.parse(builder, isPattern = false)

    val result = getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS if parseCollectiveGiven() => true
      case _ if parseGivenExpr() => true
      case _ if parseGivenTemplateBody() => true
      case _ => false
    }

    if (result) {
      faultMarker.drop()
    } else {
      faultMarker.rollbackTo()
    }
    result
  }

  def parseCollectiveGiven()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!ParamClause.parse(builder)) {
      return false
    }
    TemplateBody.parse()
    true
  }

  def parseGivenExpr()(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenType._
    import ScalaTokenTypes._
    import builder._
    if (!AsKeyword.isCurrentToken(builder)) {
      return false
    }
    val faultMarker = mark()
    AsKeyword.remapCurrentToken()
    Type.parse(builder)
    GivenParamClauses.parse()

    if (getTokenType != tASSIGN) {
      faultMarker.rollbackTo()
      return false
    }

    if (!Expr.parse(builder)) {
      faultMarker.rollbackTo()
      return false
    }

    faultMarker.drop()
    true
  }

  private def parseGivenTemplateBody()(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenType._
    import builder._

    val faultMarker = mark()

    if (AsKeyword.isCurrentToken(builder)) {
      AsKeyword.remapCurrentToken()
      advanceLexer()

      if (!builder.build(ScalaElementType.TEMPLATE_PARENTS)(parseConstructorApps)) {
        faultMarker.rollbackTo()
        return false
      }
    }

    GivenParamClauses.parse()

    if (!TemplateBody.parse()) {
      faultMarker.rollbackTo()
      return false
    }

    faultMarker.drop()
    true
  }

  private def parseConstructorApps(builder: ScalaPsiBuilder): Boolean = {
    import builder._
    if (!Constructor.parse(builder)) {
      return false
    }

    while (getTokenType == ScalaTokenTypes.tCOMMA) {
      advanceLexer()
      if (!Constructor.parse(builder)) {
        return false
      }
    }
    true
  }
}
