package org.jetbrains.plugins.scala.lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{kWITH, tCOMMA}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

sealed abstract class Parents(val allowCommaSeparatedParentsInScala3: Boolean = true) extends ParsingRule {
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
      case `kWITH` | CommaIfAllowed() if continue =>
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

  private object CommaIfAllowed {
    def unapply(element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean =
      element == tCOMMA && builder.isScala3 && allowCommaSeparatedParentsInScala3
  }
}


/**
 * [[NewExprParents]] ::= [[Constructor]] { ('with' | ',') [[AnnotType]] }
 */
object NewExprParents extends Parents(allowCommaSeparatedParentsInScala3 = false) {

  override protected def parseFirstParent()(implicit builder: ScalaPsiBuilder): Boolean =
    Constructor.parse(builder)
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
 * [[ConstrApps]] ::= [[ConstrApp]] { ('with' | ',') [[ConstrApp]] }
 */
object ConstrApps extends Parents {

  override protected def parseParent()(implicit builder: ScalaPsiBuilder): Boolean =
    Constructor.parse(builder)
}