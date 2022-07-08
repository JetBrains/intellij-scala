package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait Import extends ParsingRule {

  import lexer.ScalaTokenTypes._

  protected def keywordType: IElementType

  protected def elementType: IElementType

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    parseKeyword(keywordType, isObligatory = true)
    parseImportExpressions()

    marker.done(elementType)

    true
  }

  protected def parseKeyword(keywordType: IElementType,
                             isObligatory: Boolean)
                            (implicit builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case `keywordType` => builder.advanceLexer() // Ate keyword
      case _ if isObligatory => builder.error(ScalaBundle.message("unreachable.error"))
      case _ =>
    }

  @annotation.tailrec
  private def parseImportExpressions()(implicit builder: ScalaPsiBuilder): Unit = {
    ImportExpr()
    builder.getTokenType match {
      case `tCOMMA` =>
        builder.advanceLexer() // Ate ,
        parseImportExpressions()
      case _ =>
    }
  }
}

/**
 * [[Import]] ::= 'import' [[ImportExpr]] { ','  [[ImportExpr]] }
 */
//noinspection TypeAnnotation
object Import extends Import {

  override protected def keywordType = lexer.ScalaTokenTypes.kIMPORT

  override protected def elementType = ScalaElementType.ImportStatement
}

/**
 * [[Export]] ::= 'export' [ 'given' ] [[ImportExpr]] { ',' [[ImportExpr]] }
 */
//noinspection TypeAnnotation
object Export extends Import {

  import lexer.ScalaTokenType._

  override protected def keywordType = ExportKeyword

  override protected def elementType = ScalaElementType.ExportStatement

  override protected def parseKeyword(keywordType: IElementType,
                                      isObligatory: Boolean)
                                     (implicit builder: ScalaPsiBuilder): Unit = {
    super.parseKeyword(keywordType, isObligatory)
    super.parseKeyword(GivenKeyword, isObligatory = false)
  }
}