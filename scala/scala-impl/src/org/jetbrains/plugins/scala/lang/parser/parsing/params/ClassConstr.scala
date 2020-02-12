package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrMods
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/**
 * [[ClassConstr]] ::= [ [[TypeParamClause]] //ClsTypeParamClause] [ [[ConstrMods]] ] [[ClassParamClauses]]
 */
abstract class ClassConstr(val dropConstructorIfEmpty: Boolean) extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    TypeParamClause.parse(builder)

    val idx = builder.getCurrentOffset
    val constructorMarker = builder.mark()
    ConstrMods()
    ClassParamClauses()
    if (dropConstructorIfEmpty && idx == builder.getCurrentOffset) {
      constructorMarker.rollbackTo()
    } else {
      constructorMarker.done(ScalaElementType.PRIMARY_CONSTRUCTOR)
    }

    true
  }
}

object ClassConstr extends ClassConstr(dropConstructorIfEmpty = false)

object TraitConstr extends ClassConstr(dropConstructorIfEmpty = true)
