package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.{Body, EnumBody, TemplateBody}

sealed abstract class Template extends ParsingRule {

  import lexer.ScalaTokenTypes._

  protected def parentsRule: Parents = ClassParents

  protected def bodyRule: Body = TemplateBody

  /*
  May be hard to read. Because written before understanding that before TemplateBody could be nl token
  So there are fixed it, but may be should be some rewrite.
   */
  protected def check(implicit builder: ScalaPsiBuilder): Boolean = true

  override final def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    val skipBody =
      builder.getTokenType match {
        case `kEXTENDS` | `tUPPER_BOUND` =>
          builder.advanceLexer() // Ate extends

          builder.getTokenType match {
            case `tLBRACE` if !EarlyDef.parse(builder) => false
            case _ =>
              parentsRule()

              builder.getTokenType match {
                case `tLBRACE` if !check => true
                case _ => false
              }
          }
        case `tLBRACE` if !check => true
        case _ => false
      }

    if (!skipBody) {
      bodyRule()
    }

    marker.done(ScalaElementType.EXTENDS_BLOCK)
    true
  }
}

/**
 * [[ClassTemplate]] ::= 'extends' [[ClassTemplateBlock]] | [ ['extends'] [[TemplateBody]] ]
 */
object ClassTemplate extends Template

/**
 * [[TraitTemplate]] ::= 'extends' [[TraitTemplate]] | [ ['extends'] [[TemplateBody]] ]
 */
object TraitTemplate extends Template {

  override protected def parentsRule: MixinParents.type = MixinParents

  override protected def check(implicit builder: ScalaPsiBuilder): Boolean =
    !builder.twoNewlinesBeforeCurrentToken
}

/**
 * [[EnumTemplate]] ::= [[InheritClauses]] [[EnumBody]]
 */
object EnumTemplate extends Template {

  override protected def bodyRule: EnumBody.type = EnumBody
}