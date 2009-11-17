package org.jetbrains.plugins.scala
package lang

/**
 * @author ilyas
 */

import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import parser.ScalaElementTypes

object TokenSets {

  val MODIFIERS = TokenSet.create(ScalaTokenTypes.kCASE, ScalaTokenTypes.kABSTRACT, ScalaTokenTypes.kLAZY,
    ScalaTokenTypes.kIMPLICIT, ScalaTokenTypes.kFINAL, ScalaTokenTypes.kOVERRIDE, ScalaTokenTypes.kPROTECTED,
    ScalaTokenTypes.kPRIVATE, ScalaTokenTypes.kSEALED)

  val PROPERTY_NAMES = TokenSet.create(tIDENTIFIER)

  val TMPL_OR_PACKAGING_DEF_BIT_SET = TokenSet.create(PACKAGING, OBJECT_DEF, CLASS_DEF, TRAIT_DEF, FUNCTION_DEFINITION)

  val PACKAGING_BIT_SET = TokenSet.create(PACKAGING)

  val IMPORT_STMT_BIT_SET = TokenSet.create(IMPORT_STMT)

  val IMPORT_EXPR_BIT_SET = TokenSet.create(IMPORT_EXPR)

  val SELECTOR_BIT_SET = TokenSet.create(IMPORT_SELECTOR)

  val TMPL_DEF_BIT_SET = TokenSet.create(OBJECT_DEF, CLASS_DEF, TRAIT_DEF)

  val TMPL_OR_TYPE_BIT_SET = TokenSet.create(OBJECT_DEF,
      CLASS_DEF,
      TRAIT_DEF,
      TYPE_DEFINITION,
      TYPE_DECLARATION)

  val EXPR1_BIT_SET: TokenSet = TokenSet.create(IF_STMT,
      FOR_STMT,
      WHILE_STMT,
      DO_STMT,
      TRY_STMT,
      TRY_BLOCK,
      CATCH_BLOCK,
      FINALLY_BLOCK,
      RETURN_STMT,
      METHOD_CLOSURE,
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

  val TYPE_DEFINITIONS_SET: TokenSet = TokenSet.create(CLASS_DEF, TRAIT_DEF, OBJECT_DEF)

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
          PARENT_EXPR))

  val SIMPLE_EXPR_BIT_SET = TokenSet.create(PLACEHOLDER_EXPR,
      LITERAL,
      BLOCK_EXPR)

  val REFERENCE_SET = TokenSet.create(REFERENCE)

  val ID_SET = TokenSet.create(tIDENTIFIER, tUNDER)

  val TYPE_PARAMS_SET = TokenSet.create(TYPE_PARAM, VARIANT_TYPE_PARAM)

  val SELF_TYPE_ID = TokenSet.create(ScalaTokenTypes.kTHIS, ScalaTokenTypes.tIDENTIFIER)

  val ALIASES_SET = TokenSet.create(ScalaElementTypes.TYPE_DECLARATION, ScalaElementTypes.TYPE_DEFINITION)

  val FUNCTIONS = TokenSet.create(ScalaElementTypes.FUNCTION_DECLARATION, ScalaElementTypes.FUNCTION_DEFINITION)

  val VALUES = TokenSet.create(ScalaElementTypes.VALUE_DECLARATION, ScalaElementTypes.PATTERN_DEFINITION)

  val VARIABLES = TokenSet.create(ScalaElementTypes.VARIABLE_DECLARATION, ScalaElementTypes.VARIABLE_DEFINITION)

  val TEMPLATE_PARENTS = TokenSet.create(ScalaElementTypes.CLASS_PARENTS, ScalaElementTypes.TRAIT_PARENTS)

  val MEMBERS = TokenSet.orSet(FUNCTIONS, TokenSet.orSet(
      ALIASES_SET, TokenSet.orSet(
        TMPL_DEF_BIT_SET, TokenSet.orSet(
          VALUES, TokenSet.orSet(
            VARIABLES, TokenSet.create(ScalaElementTypes.PRIMARY_CONSTRUCTOR)
          )
        )
      )
    ))

  val DECLARED_ELEMENTS_HOLDER = TokenSet.orSet(FUNCTIONS, TokenSet.orSet(VALUES, VARIABLES))

  val PARAMETERS = TokenSet.create(PARAM, CLASS_PARAM)
}
