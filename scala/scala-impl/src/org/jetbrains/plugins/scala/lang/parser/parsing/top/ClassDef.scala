package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * ClassDef ::= id [TypeParamClause] {Annotation} [AcessModifier] [ClassParamClauses] ClassTemplateOpt
 */
object ClassDef extends ClassDef {
  override protected def classParamClauses = ClassParamClauses
  override protected def constrMods = ConstrMods
}

trait ClassDef {
  protected def classParamClauses: ClassParamClauses
  protected def constrMods: ConstrMods

  def parse(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.tIDENTIFIER =>
      builder.advanceLexer() //Ate identifier
      TypeParamClause.parse(builder)

      val constructorMarker = builder.mark()
      parseAnnotations(builder)
      constrMods.parse(builder)
      classParamClauses.parse(builder)
      constructorMarker.done(ScalaElementTypes.PRIMARY_CONSTRUCTOR)

      //parse extends block
      ClassTemplateOpt.parse(builder)
      true
    case _ =>
      builder.error(ErrMsg("identifier.expected"))
      false
  }

  private def parseAnnotations(builder: ScalaPsiBuilder): Unit = {
    val modifierMarker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      while (Annotation.parse(builder)) {}
    }
    modifierMarker.done(ScalaElementTypes.ANNOTATIONS)
  }
}