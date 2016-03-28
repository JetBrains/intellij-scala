package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */

/*
 * InfixType ::= RefinedType {id [nl] RefinedType}
 */
object InfixType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.InfixType {
  override protected val componentType = RefinedType
  override protected val errorMessage = ScalaBundle.message("refined.type.expected")

  override protected def parseId(builder: ScalaPsiBuilder, elementType: IElementType) {
    super.parseId(builder, builder.getTokenText match {
      case "&" => ScalaTokenTypes.tAND
      case "|" => ScalaTokenTypes.tOR
      case _ => elementType
    })
  }
}
