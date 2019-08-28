package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateBodyElementType

import scala.annotation.tailrec

/**
 * @author Alexander Podkhalyuzin
 *         Date: 08.02.2008
 */
trait TemplateBody extends ParsingRule {

  import lexer.ScalaTokenTypes._

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val templateBodyMarker = builder.mark
    //Look for {
    builder.enableNewlines()
    builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer() // Ate {
      case _ =>
        builder.error(ScalaBundle.message("lbrace.expected"))
    }

    SelfType.parse(builder)
    parseStatements()

    builder.restoreNewlinesState()
    templateBodyMarker.done(elementType)
    true
  }

  protected def elementType: ScTemplateBodyElementType

  protected def parseStatement()(implicit builder: ScalaPsiBuilder): Boolean

  @tailrec
  private def parseStatements()(implicit builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case null =>
      builder.error(ScalaBundle.message("rbrace.expected"))
      true
    case `tRBRACE` =>
      builder.advanceLexer() // Ate }
      true
    case _ =>
      if (parseStatement()) {
        builder.getTokenType match {
          case `tRBRACE` =>
            builder.advanceLexer() //Ate }
            true
          case `tSEMICOLON` =>
            while (builder.getTokenType == `tSEMICOLON`) builder.advanceLexer()
            parseStatements()
          case _ =>
            if (!builder.newlineBeforeCurrentToken) {
              builder.error(ScalaBundle.message("semi.expected"))
              builder.advanceLexer() // Ate something
            }

            parseStatements()
        }
      } else {
        builder.error(ScalaBundle.message("def.dcl.expected"))
        builder.advanceLexer() // Ate something
        parseStatements()
      }
  }
}

/**
 * [[TemplateBody]] ::= [nl] '{' [ [[SelfType]]  [[TemplateStat]] { semi [[TemplateStat]] } '}'
 */
object TemplateBody extends TemplateBody {

  override protected def elementType: ScTemplateBodyElementType =
    ScalaElementType.TEMPLATE_BODY

  override protected def parseStatement()(implicit builder: ScalaPsiBuilder): Boolean =
    TemplateStat.parse(builder)
}
