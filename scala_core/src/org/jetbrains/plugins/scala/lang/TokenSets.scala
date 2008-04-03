package org.jetbrains.plugins.scala.lang

/**
 * @author ilyas
 */

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaLanguage
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._

object TokenSets {

  val PROPERTY_NAMES = TokenSet.create(Array(tIDENTIFIER))

  val TMPL_OR_PACKAGING_DEF_BIT_SET = TokenSet.create(Array.apply(PACKAGING, OBJECT_DEF, CLASS_DEF, TRAIT_DEF))

  val PACKAGING_BIT_SET = TokenSet.create(Array.apply(PACKAGING))

  val PACKAGE_STMT_BIT_SET = TokenSet.create(Array.apply(PACKAGE_STMT))

  val IMPORT_STMT_BIT_SET = TokenSet.create(Array.apply(IMPORT_STMT))

  val IMPORT_EXPR_BIT_SET = TokenSet.create(Array.apply(IMPORT_EXPR))

  val SELECTOR_BIT_SET = TokenSet.create(Array.apply(IMPORT_SELECTOR))

  val TMPL_DEF_BIT_SET = TokenSet.create(Array.apply(OBJECT_DEF, CLASS_DEF, TRAIT_DEF))

  val TMPL_OR_TYPE_BIT_SET = TokenSet.create(Array.apply(OBJECT_DEF,
      CLASS_DEF,
      TRAIT_DEF,
      TYPE_DEFINITION,
      TYPE_DECLARATION))

  //todo: add cases
  val EXPR1_BIT_SET: TokenSet = TokenSet.create(Array.apply(IF_STMT,
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
      SIMPLE_EXPR,
      PREFIX_EXPR))

  val STABLE_ID_BIT_SET = TokenSet.create(Array(STABLE_ID,
      tIDENTIFIER))

  val TYPE_BIT_SET: TokenSet = TokenSet.orSet(Array(STABLE_ID_BIT_SET,
      TokenSet.create(Array.apply(SIMPLE_TYPE,
          COMPOUND_TYPE,
          INFIX_TYPE,
          TYPE,
          TYPES,
          COMPOSITE_TYPE))))

  val EXPRESSION_BIT_SET = TokenSet.orSet(Array(EXPR1_BIT_SET,
      TokenSet.create(Array.apply(LITERAL,
          STRING_LITERAL,
          BOOLEAN_LITERAL,
          PREFIX_EXPR,
          PREFIX,
          POSTFIX_EXPR,
          INFIX_EXPR,
          SIMPLE_EXPR,
          EXPR1,
          FUNCTION_EXPR,
          AN_FUN,
          BINDING,
          GENERATOR,
          ENUMERATOR,
          ENUMERATORS,
          BINDINGS,
          EXPRS,
          ARG_EXPRS,
          BLOCK_EXPR,
          ERROR_STMT,
          BLOCK,
          BLOCK_STAT))))

  val SIMPLE_EXPR_BIT_SET = TokenSet.create(Array.apply(SIMPLE_EXPR,
      LITERAL,
      BLOCK_EXPR))

  val REFERENCE_SET = TokenSet.create(Array(REFERENCE))

}
