package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes._
import org.jetbrains.plugins.dotty.lang.parser.util.ParserUtils.parseList
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */

/*
 * WithType ::= AnnotType {`with' AnnotType}
 */
object WithType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Type {
  override protected val infixType = InfixType

  override def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean) = {
    parseList(builder, kWITH, WITH_TYPE, ScalaBundle.message("wrong.type")) {
      AnnotType.parse(builder, star, isPattern)
    }
  }
}
