package org.jetbrains.plugins.scala.lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
  * @author adkozlov
  */
sealed abstract class Parents(protected val elementType: IElementType) {

  protected def parseFirstParent(builder: ScalaPsiBuilder): Boolean

  def parse(implicit builder: ScalaPsiBuilder): Boolean =
    builder.build(elementType) { builder =>
      parseFirstParent(builder) && {
        var continue = true
        while (continue && builder.getTokenType == ScalaTokenTypes.kWITH) {
          builder.advanceLexer() // Ate with
          continue = Parents.parseSimpleType(builder)
        }

        true
      }
    }
}

object Parents {

  private[top] def parseSimpleType(builder: ScalaPsiBuilder) = {
    val result = AnnotType.parse(builder, isPattern = false)

    if (!result) {
      builder.error(ScalaBundle.message("wrong.simple.type"))
    }

    result
  }
}

/*
 *  TemplateParents ::= Constr {`with' AnnotType}
 */
object ClassParents extends Parents(ScalaElementTypes.CLASS_PARENTS) {

  override protected def parseFirstParent(builder: ScalaPsiBuilder): Boolean =
    Constructor.parse(builder)
}

/*
 * MixinParents ::= AnnotType {`with' AnnotType}
 */
object MixinParents extends Parents(ScalaElementTypes.TRAIT_PARENTS) {

  override protected def parseFirstParent(builder: ScalaPsiBuilder): Boolean =
    Parents.parseSimpleType(builder)
}