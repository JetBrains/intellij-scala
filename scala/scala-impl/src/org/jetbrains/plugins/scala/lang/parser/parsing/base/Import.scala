package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStatementElementType

/**
 * @author Alexander Podkhalyuzin
 *         Date: 08.02.2008
 */
trait Statement extends ParsingRule {

  import lexer.{ScalaTokenType, ScalaTokenTypes}

  protected def isTargetTokenType(elementType: IElementType)
                                 (implicit builder: ScalaPsiBuilder): Boolean

  protected def elementType: ScStatementElementType

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val statementMarker = builder.mark()

    if (isTargetTokenType(builder.getTokenType)) {
      builder.advanceLexer() // Ate import
    } else {
      builder.error(ScalaBundle.message("unreachable.error"))
    }

    builder.getTokenType match {
      case ScalaTokenType.IsGiven() => builder.advanceLexer() // Ate given
      case _ =>
    }

    ImportExpr.parse(builder)
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() // Ate ,
      ImportExpr.parse(builder)
    }

    statementMarker.done(elementType)
    true
  }
}

/**
 * [[Import]] ::= 'import' ['given'] [[ImportExpr]] { ',' [[ImportExpr]] }
 */
object Import extends Statement {


  override protected def isTargetTokenType(elementType: IElementType)
                                          (implicit builder: ScalaPsiBuilder): Boolean =
    elementType == lexer.ScalaTokenTypes.kIMPORT

  override protected def elementType: ScStatementElementType = ScalaElementType.IMPORT_STMT
}

/**
 * [[Export]]::= 'export' ['given'] [[ImportExpr]] { ',' [[ImportExpr]] }
 */
object Export extends Statement {

  override protected def isTargetTokenType(elementType: IElementType)
                                          (implicit builder: ScalaPsiBuilder): Boolean =
    lexer.ScalaTokenType.IsExport.unapply(elementType)

  override protected def elementType: ScStatementElementType = ScalaElementType.EXPORT_STMT
}