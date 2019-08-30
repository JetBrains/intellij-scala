package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
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
object GivenDef {
  def parse()(implicit builder: ScalaPsiBuilder): Option[IElementType] = {
    import ScalaElementType._
    import ScalaTokenType._
    import ScalaTokenTypes._
    import builder._

    // given is eaten by TempDef
    //if (!GivenKeyword.isCurrentToken(builder)) {
    //  return None
    //}
    val faultMarker = builder.mark()
    //GivenKeyword.remapCurrentToken()
    //advanceLexer() // Ate given

    if (getTokenType == tIDENTIFIER && !GivenKeyword.isCurrentToken(builder) && !AsKeyword.isCurrentToken(builder)) {
      advanceLexer() // eat name of the given instance
    }

    TypeArgs.parse(builder, isPattern = false)

    val result = getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS if parseCollectiveGiven() => Some(GIVEN_DEFINITION)
      case _ if parseGivenExpr() => Some(GIVEN_ALIAS)
      case _ if parseGivenTemplateBody() => Some(GIVEN_DEFINITION)
      case _ => None
    }

    if (result.isDefined) {
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

    val extendsBlockMarker = builder.mark()
    TemplateBody.parse()
    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)
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
    advanceLexer() // eat as

    Type.parse(builder)
    GivenParamClauses.parse()

    if (getTokenType != tASSIGN) {
      faultMarker.rollbackTo()
      return false
    }
    advanceLexer() // eat =

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

    val extendsBlockMarker = mark()
    if (!TemplateBody.parse()) {
      extendsBlockMarker.drop()
      faultMarker.rollbackTo()
      return false
    }
    extendsBlockMarker.done(ScalaElementType.EXTENDS_BLOCK)

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
