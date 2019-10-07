package org.jetbrains.plugins.scala.lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kWITH
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

sealed abstract class Parents extends ParsingRule {

  protected def parseFirstParent()(implicit builder: ScalaPsiBuilder): Boolean =
    parseSimpleType()

  protected def parseParent()(implicit builder: ScalaPsiBuilder): Boolean =
    AnnotType.parse(builder, isPattern = false)

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    parseSimpleTypes(continue = parseFirstParent())

    marker.done(ScalaElementType.TEMPLATE_PARENTS)
    true
  }

  @annotation.tailrec
  private def parseSimpleTypes(continue: Boolean)
                              (implicit builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case `kWITH` if continue =>
        builder.advanceLexer() // Ate with
        parseSimpleTypes(continue = parseSimpleType())
      case _ =>
    }

  private def parseSimpleType()(implicit builder: ScalaPsiBuilder): Boolean = {
    val result = parseParent()

    if (!result) {
      builder.error(ScalaBundle.message("wrong.simple.type"))
    }

    result
  }
}

/**
 * [[ClassParents]] ::= [[Constructor]] { 'with' [[AnnotType]] }
 */
object ClassParents extends Parents {

  override protected def parseFirstParent()(implicit builder: ScalaPsiBuilder): Boolean =
    Constructor.parse(builder)
}

/**
 * [[MixinParents]] ::= [[AnnotType]] { 'with' [[AnnotType]] }
 */
object MixinParents extends Parents

/**
 * [[ConstrApps]] ::= [[ConstrApp]] { 'with' [[ConstrApp]] }
 * | [[ConstrApp]] { ',' [[ConstrApp]] }
 */
object ConstrApps extends Parents {

  override protected def parseParent()(implicit builder: ScalaPsiBuilder): Boolean =
    Constructor.parse(builder)
}