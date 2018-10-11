package org.jetbrains.plugins.scala
package lang

/**
  * @author ilyas
  */

import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes._
import org.jetbrains.plugins.scala.util.MemberElementTypesExtension

object TokenSets {

  val TYPE_DEFINITIONS: TokenSet = TokenSet.create(OBJECT_DEFINITION, CLASS_DEFINITION, TRAIT_DEFINITION)

  val WHITESPACE_OR_COMMENT_SET: TokenSet = TokenSet.create(tWHITE_SPACE_IN_LINE, tLINE_COMMENT, tBLOCK_COMMENT, tDOC_COMMENT, SCALA_DOC_COMMENT)

  val MODIFIERS: TokenSet = TokenSet.create(kCASE, kABSTRACT, kLAZY,
    kIMPLICIT, kFINAL, kOVERRIDE, kPROTECTED,
    kPRIVATE, kSEALED)

  val ID_SET: TokenSet = TokenSet.create(tIDENTIFIER, tUNDER)

  val SELF_TYPE_ID: TokenSet = TokenSet.create(kTHIS, tIDENTIFIER, tUNDER)

  val ALIASES_SET: TokenSet = TokenSet.create(TYPE_DECLARATION, TYPE_DEFINITION)

  val FUNCTIONS: TokenSet = TokenSet.create(FUNCTION_DECLARATION, FUNCTION_DEFINITION, MACRO_DEFINITION)

  val VALUES: TokenSet = TokenSet.create(VALUE_DECLARATION, PATTERN_DEFINITION)

  val VARIABLES: TokenSet = TokenSet.create(VARIABLE_DECLARATION, VARIABLE_DEFINITION)

  val MEMBERS: TokenSet =
    FUNCTIONS ++ ALIASES_SET ++ TYPE_DEFINITIONS ++ VALUES ++ VARIABLES + PRIMARY_CONSTRUCTOR ++
      MemberElementTypesExtension.getAllElementTypes

  val DECLARED_ELEMENTS_HOLDER: TokenSet = TokenSet.orSet(FUNCTIONS, TokenSet.orSet(VALUES, VARIABLES))

  val PARAMETERS: TokenSet = TokenSet.create(PARAM, CLASS_PARAM)

  val TYPE_ELEMENTS_TOKEN_SET: TokenSet = TokenSet.create(
    SIMPLE_TYPE, TYPE, TYPE_IN_PARENTHESIS, TYPE_GENERIC_CALL, INFIX_TYPE, TUPLE_TYPE,
    EXISTENTIAL_TYPE, COMPOUND_TYPE, ANNOT_TYPE, WILDCARD_TYPE, TYPE_PROJECTION, TYPE_VARIABLE, LITERAL_TYPE
  )

  implicit class TokenSetExt(val set: TokenSet) extends AnyVal {
    def ++ (other: TokenSet): TokenSet = TokenSet.orSet(set, other)
    def + (other: IElementType): TokenSet = TokenSet.orSet(set, TokenSet.create(other))
  }
}
