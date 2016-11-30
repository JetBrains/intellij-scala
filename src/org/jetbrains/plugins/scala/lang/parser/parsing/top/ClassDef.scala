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
  override protected def templateOpt = ClassTemplateOpt
  override protected def annotation = Annotation
  override protected def constrMods = ConstrMods
  override protected def typeParamClause = TypeParamClause
}

trait ClassDef {
  protected def classParamClauses: ClassParamClauses
  protected def templateOpt: TemplateOpt
  protected def annotation: Annotation
  protected def constrMods: ConstrMods
  protected def typeParamClause: TypeParamClause

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