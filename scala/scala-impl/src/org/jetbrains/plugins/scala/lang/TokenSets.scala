package org.jetbrains.plugins.scala
package lang

/**
  * @author ilyas
  */

import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.util.MemberElementTypesExtension

object TokenSets {

  val TYPE_DEFINITIONS: TokenSet = TokenSet.create(
    ClassDefinition,
    TraitDefinition,
    EnumDefinition,
    ObjectDefinition,
    GivenDefinition
  )

  val BINDING_PATTERNS: TokenSet = TokenSet.create(
    REFERENCE_PATTERN,
    NAMING_PATTERN,
    TYPED_PATTERN
  )

  val ID_SET: TokenSet = TokenSet.create(tIDENTIFIER, tUNDER)

  val SELF_TYPE_ID: TokenSet = TokenSet.create(kTHIS, tIDENTIFIER, tUNDER)

  val ALIASES_SET: TokenSet = TokenSet.create(TYPE_DECLARATION, TYPE_DEFINITION)

  val FUNCTIONS: TokenSet = TokenSet.create(FUNCTION_DECLARATION, FUNCTION_DEFINITION, MACRO_DEFINITION)

  val ENUM_CASES: TokenSet = TokenSet.create(EnumCases)

  val PROPERTIES: TokenSet = TokenSet.create(
    VALUE_DECLARATION,
    PATTERN_DEFINITION,
    VARIABLE_DECLARATION,
    VARIABLE_DEFINITION
  )

  @volatile
  private var _MEMBERS: TokenSet = computeMemberTypes()

  def MEMBERS: TokenSet = _MEMBERS

  private def computeMemberTypes() = {
    FUNCTIONS ++ ALIASES_SET ++ TYPE_DEFINITIONS ++ PROPERTIES + PRIMARY_CONSTRUCTOR ++
      MemberElementTypesExtension.getExtraMemberTypes
  }

  MemberElementTypesExtension.EP_NAME.addChangeListener(() => _MEMBERS = computeMemberTypes(), null)

  val DECLARED_ELEMENTS_HOLDER: TokenSet = TokenSet.orSet(FUNCTIONS, PROPERTIES)

  val PARAMETERS: TokenSet = TokenSet.create(PARAM, CLASS_PARAM)

  val TYPE_ELEMENTS_TOKEN_SET: TokenSet = TokenSet.create(
    SIMPLE_TYPE, TYPE, TYPE_IN_PARENTHESIS, TYPE_GENERIC_CALL, INFIX_TYPE, TUPLE_TYPE,
    EXISTENTIAL_TYPE, COMPOUND_TYPE, ANNOT_TYPE, WILDCARD_TYPE, TYPE_PROJECTION, TYPE_VARIABLE, LITERAL_TYPE
  )

  val INTERPOLATED_PREFIX_TOKEN_SET: TokenSet = TokenSet.create(
    INTERPOLATED_PREFIX_LITERAL_REFERENCE,
    INTERPOLATED_PREFIX_PATTERN_REFERENCE,
    tINTERPOLATED_STRING_ID
  )

  implicit class TokenSetExt(private val set: TokenSet) extends AnyVal {
    def ++ (other: TokenSet): TokenSet = TokenSet.orSet(set, other)
    def ++ (other: IElementType*): TokenSet = TokenSet.orSet(set, TokenSet.create(other: _*))
    def + (other: IElementType): TokenSet = TokenSet.orSet(set, TokenSet.create(other))
    def -- (other: TokenSet): TokenSet = TokenSet.andNot(set, other)
    def -- (other: IElementType*): TokenSet = TokenSet.andNot(set, TokenSet.create(other: _*))
    def - (other: IElementType): TokenSet = set -- TokenSet.create(other)
  }
}
