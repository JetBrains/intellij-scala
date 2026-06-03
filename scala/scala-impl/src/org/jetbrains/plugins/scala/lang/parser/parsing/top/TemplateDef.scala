package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ClassConstr, TraitConstr, TypeParamClause}

sealed abstract class TemplateDef extends ParsingRule {

  protected def parseConstructor()(implicit builder: ScalaPsiBuilder): Unit = {}

  protected def extendsBlockRule: Template

  override final def parse(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() // Ate identifier

        parseConstructor()
        extendsBlockRule()

        true
      case _ =>
        builder.error(ScalaBundle.message("identifier.expected"))
        false
    }
}

/**
 * * [[ClassDef]] ::= id [[ClassConstr]] [ [[ClassTemplate]] ]
 */
object ClassDef extends TemplateDef {

  override protected def parseConstructor()(implicit builder: ScalaPsiBuilder): Unit =
    ClassConstr()

  override protected def extendsBlockRule: ClassTemplate.type = ClassTemplate
}

/**
 * [[TraitDef]] ::= id [ [[TypeParamClause]] ] [ [[TraitTemplate]] ]
 */
object TraitDef extends TemplateDef {

  override protected def parseConstructor()(implicit builder: ScalaPsiBuilder): Unit =
    TraitConstr()

  override protected def extendsBlockRule: TraitTemplate.type = TraitTemplate
}

/**
 * [[ObjectDef]] ::= id [ [[ClassTemplate]] ]
 */
object ObjectDef extends TemplateDef {

  override protected def extendsBlockRule: ClassTemplate.type = ClassTemplate
}

/**
 * [[EnumDef]] ::= id [[ClassConstr]] [[EnumTemplate]]
 */
object EnumDef extends TemplateDef {

  override protected def parseConstructor()(implicit builder: ScalaPsiBuilder): Unit =
    ClassConstr()

  override protected def extendsBlockRule: EnumTemplate.type = EnumTemplate
}
