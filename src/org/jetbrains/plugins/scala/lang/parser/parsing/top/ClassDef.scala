package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * ClassDef ::= id [TypeParamClause] {Annotation} [AcessModifier] [ClassParamClauses] ClassTemplateOpt
 */
object ClassDef extends ClassDef {
  override protected val classParamClauses = ClassParamClauses
  override protected val templateOpt = ClassTemplateOpt
  override protected val annotation = Annotation
  override protected val constrMods = ConstrMods
  override protected val typeParamClause = TypeParamClause
}

trait ClassDef {
  protected val classParamClauses: ClassParamClauses
  protected val templateOpt: ClassTemplateOpt
  protected val annotation: Annotation
  protected val constrMods: ConstrMods
  protected val typeParamClause: TypeParamClause

  def parse(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.tIDENTIFIER =>
      builder.advanceLexer() //Ate identifier
      typeParamClause.parse(builder)

      val constructorMarker = builder.mark()
      parseAnnotations(builder)
      constrMods.parse(builder)
      classParamClauses.parse(builder)
      constructorMarker.done(ScalaElementTypes.PRIMARY_CONSTRUCTOR)

      //parse extends block
      templateOpt.parse(builder)
      true
    case _ =>
      builder.error(ErrMsg("identifier.expected"))
      false
  }

  private def parseAnnotations(builder: ScalaPsiBuilder) = {
    val modifierMarker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      while (annotation.parse(builder)) {}
    }
    modifierMarker.done(ScalaElementTypes.ANNOTATIONS)
  }
}