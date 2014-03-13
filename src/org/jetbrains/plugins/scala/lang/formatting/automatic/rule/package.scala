package org.jetbrains.plugins.scala
package lang.formatting.automatic

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleInstance, IndentType}
import com.intellij.psi.tree.IElementType
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScReferencePatternElementType

//import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
//import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
//import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
//import org.jetbrains.plugins.scala.lang.formatting.automatic.rule

/**
 * @author Roman.Shein
 *         Date: 11.09.13
 */
package object rule {

  type RuleMatch = ScalaFormattingRuleInstance#RuleMatch

  val exprElementTypes = List[IElementType] (
    ScalaElementTypes.PREFIX_EXPR,
    ScalaElementTypes.PREFIX,
    ScalaElementTypes.POSTFIX_EXPR,
    ScalaElementTypes.INFIX_EXPR,
    ScalaElementTypes.PLACEHOLDER_EXPR,
    ScalaElementTypes.PARENT_EXPR,
    ScalaElementTypes.METHOD_CALL,
    ScalaElementTypes.REFERENCE_EXPRESSION,
    ScalaElementTypes.THIS_REFERENCE,
    ScalaElementTypes.SUPER_REFERENCE,
    ScalaElementTypes.GENERIC_CALL,
    ScalaElementTypes.EXPR1,
    ScalaElementTypes.FUNCTION_EXPR,
    ScalaElementTypes.AN_FUN,
    ScalaElementTypes.GENERATOR,
    ScalaElementTypes.ENUMERATOR,
    ScalaElementTypes.ENUMERATORS,
    ScalaElementTypes.GUARD,
    ScalaElementTypes.EXPRS,
    ScalaElementTypes.ARG_EXPRS,
    ScalaElementTypes.BLOCK_EXPR,
    ScalaElementTypes.CONSTR_BLOCK,
    ScalaElementTypes.ERROR_STMT,
    ScalaElementTypes.BLOCK,
    ScalaElementTypes.TUPLE,
    ScalaElementTypes.IF_STMT,
    ScalaElementTypes.FOR_STMT,
    ScalaElementTypes.WHILE_STMT,
    ScalaElementTypes.DO_STMT,
    ScalaElementTypes.TRY_STMT,
    ScalaElementTypes.TRY_BLOCK,
    ScalaElementTypes.CATCH_BLOCK,
    ScalaElementTypes.FINALLY_BLOCK,
    ScalaElementTypes.RETURN_STMT,
    ScalaElementTypes.THROW_STMT,
    ScalaElementTypes.ASSIGN_STMT,
    ScalaElementTypes.MATCH_STMT,
    ScalaElementTypes.TYPED_EXPR_STMT
  )

  val patterns = List[IElementType] (
    ScalaElementTypes.TUPLE_PATTERN,
    ScalaElementTypes.SEQ_WILDCARD,
    ScalaElementTypes.CONSTRUCTOR_PATTERN,
    ScalaElementTypes.PATTERN_ARGS,
    ScalaElementTypes.INFIX_PATTERN,
    ScalaElementTypes.NAMING_PATTERN,
    ScalaElementTypes.TYPED_PATTERN,
    ScalaElementTypes.PATTERN,
    ScalaElementTypes.PATTERNS,
    ScalaElementTypes.WILDCARD_PATTERN,
    ScalaElementTypes.CASE_CLAUSE,
    ScalaElementTypes.CASE_CLAUSES,
    ScalaElementTypes.LITERAL_PATTERN,
    ScalaElementTypes.REFERENCE_PATTERN,
    ScalaElementTypes.STABLE_REFERENCE_PATTERN,
    ScalaElementTypes.PATTERN_IN_PARENTHESIS,
    ScalaElementTypes.ARG_TYPE_PATTERN,
    ScalaElementTypes.ARG_TYPE_PATTERNS,
    ScalaElementTypes.TYPE_PATTERN_ARGS,
    ScalaElementTypes.TYPE_PATTERN,
    ScalaElementTypes.STATEMENT_SEPARATOR,
    ScalaElementTypes.IMPLICIT_END,
    ScalaElementTypes.COMPOSITE_TYPE,
    ScalaElementTypes.TYPE_WITH_TYPES,
    ScalaElementTypes.REFINEMENT
  ) //TODO: do I really need all these patterns here?

  private val rulesByNames = mutable.Map[String, ScalaFormattingRule]()

  def addRule(rule: ScalaFormattingRule) = {
    assert(!rulesByNames.contains(rule.id))
    rulesByNames.put(rule.id, rule)
    rule
  }

  def registerAnchor(rule: ScalaFormattingRule) = {
    if (!rulesByNames.contains(rule.id)) {
      rulesByNames.put(rule.id, rule)
    } else {
      assert(rulesByNames.get(rule.id).get == rule)
    }
    rule
  }

  def getRule(id: String): ScalaFormattingRule = rulesByNames.get(id).get

  //first, define simple block rules
  val leftBracket = ScalaBlockRule("[", "LEFT_BRACKET")

  val rightBracket = ScalaBlockRule("]", "RIGHT_BRACKET")

  val leftBrace = ScalaBlockRule("{", "LEFT_BRACE")

  val rightBrace = ScalaBlockRule("}", "RIGHT_BRACE")

  val leftParenthesis = ScalaBlockRule("(", "LEFT_PARENTHESIS")

  val rightParenthesis = ScalaBlockRule(")", "RIGHT_PARENTHESIS")

  val comma = ScalaBlockRule(",", "COMMA")

  val dot = ScalaBlockRule(".", "DOT")

  val semi = ScalaBlockRule(";", "SEMICOLON")

  val ifWord = ScalaBlockRule("if", "IF WORD")

  val elseWord = ScalaBlockRule("else", "ELSE WORD")

  val ifBlock = ScalaBlockRule("IF BLOCK", ScalaElementTypes.IF_STMT)

  val whileWord = ScalaBlockRule("while", "WHILE WORD")

  val tryWord = ScalaBlockRule("try", "TRY WORD")

  val catchWord = ScalaBlockRule("catch", "CATCH WORD")

  val caseWord = ScalaBlockRule("case", "CASE WORD")

  val idRule = ScalaBlockRule("ID", ScalaTokenTypes.tIDENTIFIER)

  //now, define composite rule components with increasing complexity

  val maybeSemi = MaybeRule(semi, "MAYBE SEMICOLON")

  val expr = ScalaBlockRule("EXPR", exprElementTypes) //TODO: find out if it matches correctly with the grammatics

  val block = ScalaBlockRule("BLOCK", ScalaElementTypes.BLOCK) //TODO: find out if it matches correctly with the grammatics

  val bracedExpr = ScalaFormattingCompositeRule(ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED EXPR", leftBrace, expr, rightBrace)

  val bracedBlockExpr = ScalaBlockCompositeRule(bracedExpr, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED BLOCK EXPR", exprElementTypes) // BLOCK_EXPR?

  val maybeBlockExpr = OrRule("MAYBE BRACED EXPR", bracedBlockExpr, expr)

  val maybeExpr = MaybeRule(expr,
    IndentType.NormalIndent,
    "MaybeExpr")

  val ifElse = ScalaFormattingCompositeRule(ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "IF ELSE",
    maybeSemi, elseWord, maybeBlockExpr
  )

  val elseCompositeBlock = ScalaBlockCompositeRule("else", ifElse, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "ELSE COMPOSITE BLOCK")

  val maybeElse = MaybeRule(elseCompositeBlock,
    IndentType.NormalIndent,
    "MAYBE ELSE"
  )

  val ifComposite = ScalaFormattingCompositeRule("IF COMPOSITE",
    ifWord, leftParenthesis, expr, rightParenthesis, maybeBlockExpr
  )

  val ifCompositeBlock = ScalaBlockCompositeRule("if", ifComposite, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "IF COMPOSITE BLOCK")

  val whileComposite = ScalaFormattingCompositeRule("WHILE COMPOSITE",
    whileWord,
    leftParenthesis,
    expr,
    rightParenthesis,
    expr)

  val `=>` = ScalaBlockRule("=>", "=>")

  val pattern = ScalaBlockRule("PATTERN BLOCK", patterns)

  val guard = ScalaBlockRule("GUARD", ScalaElementTypes.GUARD)

  val maybeGuard = MaybeRule(guard, "MAYBE GUARD")

  val caseClauseArrowAnchor = "CASE CLAUSE ARROW ANCHOR"

  val caseClause = ScalaBlockCompositeRule(ScalaElementTypes.CASE_CLAUSE,
    ScalaFormattingCompositeRule("CASE CLAUSE", caseWord, pattern, maybeGuard, `=>`.anchor(caseClauseArrowAnchor), block)
    , ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CASE CLAUSE COMPOSITE")

  val caseClauses = ScalaSomeRule(1, caseClause.anchor("CASE CLAUSE ANCHOR"), "CASE CLAUSES")

  val caseClausesComposite = ScalaBlockCompositeRule(ScalaElementTypes.CASE_CLAUSES, caseClauses, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CASE CLAUSES COMPOSITE")

  val caseClausesMonolithic = ScalaBlockRule("CASE CLAUSES MONOLITHIC", ScalaElementTypes.CASE_CLAUSES)

  val matchWord = ScalaBlockRule("match", "MATCH WORD")

  val matchRule = ScalaBlockCompositeRule(ScalaElementTypes.MATCH_STMT, ScalaFormattingCompositeRule("MATCH COMPOSITE", expr, matchWord, leftBrace, caseClausesMonolithic, rightBrace), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "MATCH RULE")

  val bracedMonolithicClausesBlock = ScalaBlockCompositeRule(ScalaElementTypes.BLOCK_EXPR, ScalaFormattingCompositeRule("BRACED MONOLITHIC CLAUSES BLOCK COMPOSITE", leftBrace, caseClausesMonolithic, rightBrace), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED MONOLITHIC CLAUSES BLOCK")

  val catchRule = ScalaFormattingCompositeRule("CATCH RULE", catchWord, bracedMonolithicClausesBlock)

  val catchBlock = ScalaBlockCompositeRule(ScalaElementTypes.CATCH_BLOCK, catchRule, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CATCH BLOCK")

  val maybeCatchBlock = MaybeRule(catchBlock,
    IndentType.NormalIndent,
    "MAYBE CATCH BLOCK")

  val tryBlock = ScalaBlockCompositeRule(ScalaElementTypes.TRY_BLOCK, ScalaFormattingCompositeRule("TRY BLOCK COMPOSITE", tryWord, bracedExpr), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "TRY BLOCK")

  val tryComposite = ScalaFormattingCompositeRule("TRY COMPOSITE",
    tryBlock, maybeCatchBlock)

  //now, define resulting rules

  val ifDefault = ScalaBlockCompositeRule(ScalaElementTypes.IF_STMT,
    ScalaFormattingCompositeRule("IF DEFAULT COMPOSITE", ifCompositeBlock, maybeElse),
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "IF DEFAULT")

  val whileDefault = ScalaBlockCompositeRule(ScalaElementTypes.WHILE_STMT,
    whileComposite,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "WHILE DEFAULT")

  val tryDefault = ScalaBlockCompositeRule(ScalaElementTypes.TRY_STMT,
    tryComposite,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "TRY DEFAULT") //or maybe TRY_BLOCK ???

  val idChainAnchor = "ID CHAIN ANCHOR"

  val idChainDefault = ScalaBlockCompositeRule(ScalaElementTypes.REFERENCE_EXPRESSION,
    ScalaFormattingCompositeRule(
      "ID CHAIN COMPOSITE",
      ScalaSomeRule(1,
        ScalaFormattingCompositeRule(
          "ID CHAIN HEAD COMPOSITE",
          idRule.anchor(idChainAnchor),
          dot), "ID CHAIN HEAD"
      ),
      idRule.anchor(idChainAnchor)
    ), //TODO: is it really continuation?
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "ID CHAIN DEFAULT"
  )

//  val idChainDefaultId = "ID CHAIN DEFAULT"
//
//  val idChainDefault = ScalaBlockCompositeRule(ScalaElementTypes.REFERENCE_EXPRESSION,
//    ScalaFormattingCompositeRule(
//      "ID CHAIN COMPOSITE",
//      OrRule("ID CHAIN OR", IndentType.ContinuationIndent, List(idChainDefaultId, idRule.anchor(idChainAnchor).id)),
//      dot,
//      idRule.anchor(idChainAnchor)
//    ),
//    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
//    idChainDefaultId
//  )
}
