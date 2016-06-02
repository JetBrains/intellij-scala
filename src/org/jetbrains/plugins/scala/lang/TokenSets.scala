package org.jetbrains.plugins.scala
package lang

/**
  * @author ilyas
  */

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.parser.{ElementTypes, ScalaElementTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes._
import org.jetbrains.plugins.scala.util.MemberElementTypesExtension

object ScalaTokenSets extends TokenSets {
  override lazy val elementTypes = ScalaElementTypes
}

trait TokenSets {
  val elementTypes: ElementTypes

  val templateDefinitionSet = TokenSet.create(elementTypes.objectDefinition,
    elementTypes.classDefinition,
    elementTypes.traitDefinition)

  val templateOrTypeSet = TokenSet.orSet(templateDefinitionSet,
    TokenSet.create(TYPE_DEFINITION, TYPE_DECLARATION))

  val membersSet = TokenSet.orSet(TokenSet.orSet(FUNCTIONS, TokenSet.orSet(
    ALIASES_SET, TokenSet.orSet(
      templateDefinitionSet, TokenSet.orSet(
        VALUES, TokenSet.orSet(
          VARIABLES, TokenSet.create(ScalaElementTypes.PRIMARY_CONSTRUCTOR)
        )
      )
    )
  )), MemberElementTypesExtension.getAllElementTypes
  )
}

object TokenSets {
  val WHITESPACE_OR_COMMENT_SET = TokenSet.create(tWHITE_SPACE_IN_LINE, tLINE_COMMENT, tBLOCK_COMMENT, tDOC_COMMENT, SCALA_DOC_COMMENT)

  val MODIFIERS = TokenSet.create(ScalaTokenTypes.kCASE, ScalaTokenTypes.kABSTRACT, ScalaTokenTypes.kLAZY,
    ScalaTokenTypes.kIMPLICIT, ScalaTokenTypes.kFINAL, ScalaTokenTypes.kOVERRIDE, ScalaTokenTypes.kPROTECTED,
    ScalaTokenTypes.kPRIVATE, ScalaTokenTypes.kSEALED)

  val PROPERTY_NAMES = TokenSet.create(tIDENTIFIER)

  val PACKAGING_BIT_SET = TokenSet.create(PACKAGING)

  val IMPORT_STMT_BIT_SET = TokenSet.create(IMPORT_STMT)

  val IMPORT_EXPR_BIT_SET = TokenSet.create(IMPORT_EXPR)

  val SELECTOR_BIT_SET = TokenSet.create(IMPORT_SELECTOR)

  val EXPR1_BIT_SET: TokenSet = TokenSet.create(IF_STMT,
    FOR_STMT,
    WHILE_STMT,
    DO_STMT,
    TRY_STMT,
    TRY_BLOCK,
    CATCH_BLOCK,
    FINALLY_BLOCK,
    RETURN_STMT,
    THROW_STMT,
    ASSIGN_STMT,
    MATCH_STMT,
    TYPED_EXPR_STMT,
    POSTFIX_EXPR,
    INFIX_EXPR,
    PLACEHOLDER_EXPR,
    PREFIX_EXPR)

  val STABLE_ID_BIT_SET = TokenSet.create(STABLE_ID,
    tIDENTIFIER)

  val TYPE_BIT_SET: TokenSet = TokenSet.orSet(STABLE_ID_BIT_SET,
    TokenSet.create(SIMPLE_TYPE,
      COMPOUND_TYPE,
      INFIX_TYPE,
      TYPE,
      TYPES,
      COMPOSITE_TYPE))

  val EXPRESSION_BIT_SET = TokenSet.orSet(EXPR1_BIT_SET,
    TokenSet.create(LITERAL,
      STRING_LITERAL,
      BOOLEAN_LITERAL,
      PREFIX_EXPR,
      PREFIX,
      POSTFIX_EXPR,
      INFIX_EXPR,
      PLACEHOLDER_EXPR,
      EXPR1,
      FUNCTION_EXPR,
      AN_FUN,
      GENERATOR,
      ENUMERATOR,
      ENUMERATORS,
      EXPRS,
      ARG_EXPRS,
      BLOCK_EXPR,
      ERROR_STMT,
      BLOCK,
      PARENT_EXPR,
      METHOD_CALL,
      REFERENCE_EXPRESSION,
      THIS_REFERENCE,
      SUPER_REFERENCE,
      GENERIC_CALL))

  val SIMPLE_EXPR_BIT_SET = TokenSet.create(PLACEHOLDER_EXPR,
    LITERAL,
    BLOCK_EXPR)

  val REFERENCE_SET = TokenSet.create(REFERENCE)

  val ID_SET = TokenSet.create(tIDENTIFIER, tUNDER)

  val TYPE_PARAMS_SET = TokenSet.create(TYPE_PARAM, VARIANT_TYPE_PARAM)

  val SELF_TYPE_ID = TokenSet.create(ScalaTokenTypes.kTHIS, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tUNDER)

  val ALIASES_SET = TokenSet.create(ScalaElementTypes.TYPE_DECLARATION, ScalaElementTypes.TYPE_DEFINITION)

  val FUNCTIONS = TokenSet.create(ScalaElementTypes.FUNCTION_DECLARATION, ScalaElementTypes.FUNCTION_DEFINITION, ScalaElementTypes.MACRO_DEFINITION)

  val VALUES = TokenSet.create(ScalaElementTypes.VALUE_DECLARATION, ScalaElementTypes.PATTERN_DEFINITION)

  val VARIABLES = TokenSet.create(ScalaElementTypes.VARIABLE_DECLARATION, ScalaElementTypes.VARIABLE_DEFINITION)

  val TEMPLATE_PARENTS = TokenSet.create(ScalaElementTypes.CLASS_PARENTS, ScalaElementTypes.TRAIT_PARENTS)

  val DECLARED_ELEMENTS_HOLDER = TokenSet.orSet(FUNCTIONS, TokenSet.orSet(VALUES, VARIABLES))

  val PARAMETERS = TokenSet.create(PARAM, CLASS_PARAM)

  val TYPE_ELEMENTS_TOKEN_SET = TokenSet.create(
    SIMPLE_TYPE, TYPE, TYPE_IN_PARENTHESIS, TYPE_GENERIC_CALL, INFIX_TYPE, TUPLE_TYPE,
    EXISTENTIAL_TYPE, COMPOUND_TYPE, ANNOT_TYPE, WILDCARD_TYPE, TYPE_PROJECTION, TYPE_VARIABLE
  )
}
