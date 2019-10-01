package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody

sealed abstract class TemplateOpt extends ParsingRule {

  import lexer.ScalaTokenTypes._

  protected def parentsRule: Parents

  /*
  May be hard to read. Because written before understanding that before TemplateBody could be nl token
  So there are fixed it, but may be should be some rewrite.
   */
  protected def check(implicit builder: ScalaPsiBuilder): Boolean

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    builder.getTokenType match {
      case `kEXTENDS` | `tUPPER_BOUND` =>
        builder.advanceLexer() // Ate extends

        builder.getTokenType match {
          case `tLBRACE` if !EarlyDef.parse(builder) => parseTemplateBody()
          case _ =>
            parentsRule()

            builder.getTokenType match {
              case `tLBRACE` if !builder.twoNewlinesBeforeCurrentToken => parseTemplateBody()
              case _ =>
            }
        }
      case `tLBRACE` if check => parseTemplateBody()
      case _ =>
    }

    marker.done(ScalaElementType.EXTENDS_BLOCK)
    true
  }

  private final def parseTemplateBody()(implicit builder: ScalaPsiBuilder): Unit =
    TemplateBody.parse(builder)
}

/**
 * [[ClassTemplateOpt]] ::= 'extends' [[ClassTemplate]] | [ ['extends'] [[TemplateBody]] ]
 */
object ClassTemplateOpt extends TemplateOpt {

  override protected def parentsRule: ClassParents.type = ClassParents

  override protected def check(implicit builder: ScalaPsiBuilder) = true
}

/**
 * [[TraitTemplateOpt]] ::= 'extends' [[TraitTemplate]] | [ ['extends'] [[TemplateBody]] ]
 */
object TraitTemplateOpt extends TemplateOpt {

  override protected def parentsRule: MixinParents.type = MixinParents

  override protected def check(implicit builder: ScalaPsiBuilder): Boolean =
    !builder.twoNewlinesBeforeCurrentToken
}