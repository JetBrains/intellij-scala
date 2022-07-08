package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.InfixType

object ImportSelector extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val importSelectorMarker = builder.mark()
    builder.getTokenType match {
      case _ if builder.features.`Scala 3 wildcard imports in selector` && builder.tryParseSoftKeyword(ScalaTokenType.WildcardStar) =>
        importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
        return true
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer()
        importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
        return true
      case ScalaTokenType.GivenKeyword =>
        builder.advanceLexer()
        InfixType()
        importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
        return true
      case ScalaTokenTypes.tIDENTIFIER =>
        val sel = builder.mark()
        builder.advanceLexer() // Ate identifier
        sel.done(ScalaElementType.REFERENCE)
      case _ =>
        importSelectorMarker.drop()
        return false
    }

    def parseNamed(): Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER | ScalaTokenTypes.tIDENTIFIER =>
          builder.advanceLexer() //Ate _ | identifier
          importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
          true
        case _ =>
          builder error ErrMsg("identifier.or.wild.sign.expected")
          importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
          true
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate =>
        parseNamed()
      case _ if builder.features.`Scala 3 renaming imports` && builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword) =>
        parseNamed()
      case _ =>
        importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
        true
    }
  }
}
