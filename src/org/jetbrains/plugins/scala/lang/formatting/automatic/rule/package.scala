package org.jetbrains.plugins.scala
package lang.formatting.automatic

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.IElementType
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.{SameAlignmentRelation, SameSettingsRelation}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.SameSettingsRelation._
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.ScalaFormattingRuleInstance

/**
 * @author Roman.Shein
 *         Date: 11.09.13
 */
package object rule {

  val defaultPriority = ScalaFormattingRule.RULE_PRIORITY_DEFAULT

  //TODO: move this to rules companion objects, disable creation "by-name" for IDs that contains reserver IDs as substrings

  val zeroOrMoreId = "ZERO_OR_MORE "

  val oneOrMoreId = "ONE_OR_MORE "

  val maybeId = "MAYBE "

  val orId = " OR "

  val seqId = "SEQ"

  val blockTextId = " TEXT BLOCK WITH "

  val blockTypeId = " TYPE BLOCK WITH "

  val reservedIds = List(zeroOrMoreId, oneOrMoreId, maybeId, orId, seqId, blockTextId, blockTypeId)

  def maybe(rule: ScalaFormattingRule) = MaybeRule(rule, maybeId + rule.id)

  def maybe(indentType: IndentType, rule: ScalaFormattingRule) = MaybeRule(rule, indentType, maybeId + rule.id)

  def or(indentType: Option[IndentType], rules: ScalaFormattingRule*): ScalaFormattingRule = {
    assert(rules.nonEmpty)
    val id = rules.tail.foldLeft(rules.head.id)((acc, rule) => acc + orId + rule.id)
    OrRule(id, indentType, rules: _*)
  }

  def or(rules: ScalaFormattingRule*): ScalaFormattingRule = or(None, rules: _*)

  def or(indentType: IndentType, rules: ScalaFormattingRule*): ScalaFormattingRule = or(Some(indentType), rules: _*)

  def seq(rules: ScalaFormattingRule*) = {
    assert(rules.nonEmpty)
    ScalaFormattingCompositeRule(rules.tail.foldLeft(seqId + "(" + rules.head.id)((acc, rule) => acc + ", " + rule.id) + ")", rules: _*)
  }

  def block(expectedText: String,
            priority: Int,
            compositeRule: ScalaFormattingRule): ScalaFormattingRule =
    ScalaBlockCompositeRule(expectedText, compositeRule, priority, expectedText + blockTextId + compositeRule.id)

  def block(expectedText: String,
            compositeRule: ScalaFormattingRule): ScalaFormattingRule = block(expectedText, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, compositeRule)

  def block(expectedType: IElementType,
            priority: Int = ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
            compositeRule: ScalaFormattingRule): ScalaFormattingRule =
    ScalaBlockCompositeRule(expectedType, compositeRule, priority, expectedType + blockTypeId + compositeRule.id)

  def block(expectedTypes: List[IElementType], priority: Int, compositeRule: ScalaFormattingRule): ScalaFormattingRule = {
    assert(expectedTypes.nonEmpty)
    val id = expectedTypes.tail.foldLeft(expectedTypes.head.toString)(
      (acc, exType) => acc + ", " + exType
    ) + blockTypeId + compositeRule.id
    ScalaBlockCompositeRule(compositeRule, priority, id, expectedTypes)
  }

  def &(rule: ScalaFormattingRule, relationId: String, additionalIds: RelationParticipantId*) =
    rule.acceptRelation(sameSettingsRelationsByIds.get(relationId).getOrElse {
      val relation = new SameSettingsRelation()
      sameSettingsRelationsByIds.put(relationId, relation)
      relation
    }, additionalIds:_*
    )

  def alignment(rule: ScalaFormattingRule, relationId: String) =
    rule.acceptRelation(sameAlignmentRelationsByIds.get(relationId).getOrElse {
      val relation = new SameAlignmentRelation()
      sameAlignmentRelationsByIds.put(relationId, relation)
      relation
    }
    )

  val sameSettingsRelationsByIds = mutable.Map[String, SameSettingsRelation]()
  val sameAlignmentRelationsByIds = mutable.Map[String, SameAlignmentRelation]()

  //  def isAcceptableId(id: String) = reservedIds.count(id.contains) == 0

  //anchor ids
  val ALIGN_IF_ELSE_ANCHOR = "ALIGN_IF_ELSE"

  //TAGS
  val ALIGN_IF_ELSE_TAG_IF_WORD = "ALIGN_IF_ELSE_IF_WORD"
  val ALIGN_IF_ELSE_TAG_ELSE_WORD = "ALIGN_IF_ELSE_ELSE_WORD"
  val whileWordTag = "WHILE WORD"
  val catchWordTag = "CATCH WORD"
  val finallyWordTag = "FINALLY WORD"
  val idChainTag = "ID CHAIN"
  val idChainArgsTag = "ID CHAIN ARGS"
  val parametersAlignmentTag = "PARAMETERS"

  type RuleMatch = ScalaFormattingRuleInstance#RuleMatch

  val exprElementTypes = List[IElementType](
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

  val patterns = List[IElementType](
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

  /**
   * Maps pairs (rule, tag) to rule
   */
  private val rulesByNames = mutable.Map[String, ScalaFormattingRule]()

  def addRule(rule: ScalaFormattingRule) = {
    assert(!rulesByNames.contains(rule.id))
    rulesByNames.put(rule.id, rule)
    rule
  }

  //TODO: redo all this stuff
  def registerRule(rule: ScalaFormattingRule) = {
    if (!rulesByNames.contains(rule.id)) {
      rulesByNames.put(rule.id, rule)
    } else {
      //      assert(rulesByNames.get(rule.id).get == rule)
    }
    rule
  }

  def getRule(id: String): ScalaFormattingRule = rulesByNames.get(id).get

  def containsRule(id: String): Boolean = rulesByNames.contains(id)

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

  val equalitySign = ScalaBlockRule("=", "EQUALITY SIGN")

  val tryWord = ScalaBlockRule("try", "TRY WORD")

  val catchWord = ScalaBlockRule("catch", "CATCH WORD")

  val finallyWord = ScalaBlockRule("finally", "FINALLY WORD")

  val caseWord = ScalaBlockRule("case", "CASE WORD")

  val idRule = ScalaBlockRule("ID", ScalaTokenTypes.tIDENTIFIER)

  //now, define composite rule components with increasing complexity

  val maybeSemi = maybe(semi) //MaybeRule(semi, "MAYBE SEMICOLON")

  val expr = ScalaBlockRule("EXPR", exprElementTypes) //TODO: find out if it matches correctly with the grammatics

  val referenceExprRule = ScalaBlockRule("REFERENCE EXPR", ScalaElementTypes.REFERENCE_EXPRESSION)

  val block = ScalaBlockRule("BLOCK", ScalaElementTypes.BLOCK) //TODO: find out if it matches correctly with the grammatics

  val bracedExpr = ScalaFormattingCompositeRule(ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED EXPR", leftBrace, expr, rightBrace)

  val bracedBlockExpr = ScalaBlockCompositeRule(bracedExpr, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED BLOCK EXPR", exprElementTypes) // BLOCK_EXPR?

  val maybeBlockExpr = OrRule("MAYBE BRACED EXPR", bracedBlockExpr, expr)

  val maybeExpr = MaybeRule(expr,
    IndentType.NormalIndent,
    "MaybeExpr")

  val ifElse = ScalaFormattingCompositeRule(ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "IF ELSE",
    maybeSemi, alignment(elseWord, ALIGN_IF_ELSE_ANCHOR).tag(ALIGN_IF_ELSE_TAG_ELSE_WORD), maybeBlockExpr
  )

  val elseCompositeBlock = ScalaBlockCompositeRule("else", ifElse, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "ELSE COMPOSITE BLOCK")

  val maybeElse = MaybeRule(elseCompositeBlock,
    IndentType.NormalIndent,
    "MAYBE ELSE"
  )

  val ifComposite = ScalaFormattingCompositeRule("IF COMPOSITE",
    alignment(ifWord, ALIGN_IF_ELSE_ANCHOR).tag(ALIGN_IF_ELSE_TAG_IF_WORD), leftParenthesis, expr, rightParenthesis, maybeBlockExpr
  )

  val ifCompositeBlock = ScalaBlockCompositeRule("if", ifComposite, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "IF COMPOSITE BLOCK")

  val whileComposite = ScalaFormattingCompositeRule("WHILE COMPOSITE",
    whileWord.tag(whileWordTag),
    leftParenthesis,
    expr,
    rightParenthesis,
    maybeBlockExpr)

  val `=>` = ScalaBlockRule("=>", "=>")

  val `<-` = ScalaBlockRule("<-", "<-")

  val pattern = ScalaBlockRule("PATTERN BLOCK", patterns)

  val guard = ScalaBlockRule("GUARD", ScalaElementTypes.GUARD)

  val maybeGuard = MaybeRule(guard, "MAYBE GUARD")

  val caseClauseArrowAnchor = "CASE CLAUSE ARROW ANCHOR"

  val caseClause = ScalaBlockCompositeRule(ScalaElementTypes.CASE_CLAUSE,
    ScalaFormattingCompositeRule("CASE CLAUSE", caseWord, pattern, maybeGuard, `=>`.&(caseClauseArrowAnchor), block)
    , ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CASE CLAUSE COMPOSITE")

  val caseClauses = ScalaSomeRule(1, caseClause.&("CASE CLAUSE ANCHOR"), "CASE CLAUSES")

  val caseClausesComposite = ScalaBlockCompositeRule(ScalaElementTypes.CASE_CLAUSES, caseClauses, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CASE CLAUSES COMPOSITE")

  val caseClausesMonolithic = ScalaBlockRule("CASE CLAUSES MONOLITHIC", ScalaElementTypes.CASE_CLAUSES)

  val matchWord = ScalaBlockRule("match", "MATCH WORD")

  val matchRule = ScalaBlockCompositeRule(ScalaElementTypes.MATCH_STMT, ScalaFormattingCompositeRule("MATCH COMPOSITE", expr, matchWord, leftBrace, caseClausesMonolithic, rightBrace), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "MATCH RULE")

  val bracedMonolithicClausesBlock = ScalaBlockCompositeRule(ScalaElementTypes.BLOCK_EXPR, ScalaFormattingCompositeRule("BRACED MONOLITHIC CLAUSES BLOCK COMPOSITE", leftBrace, caseClausesMonolithic, rightBrace), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "BRACED MONOLITHIC CLAUSES BLOCK")

  val catchRule = ScalaFormattingCompositeRule("CATCH RULE", catchWord.tag(catchWordTag), bracedMonolithicClausesBlock)

  val catchBlock = ScalaBlockCompositeRule(ScalaElementTypes.CATCH_BLOCK, catchRule, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "CATCH BLOCK")

  val finallyRule = ScalaFormattingCompositeRule("FINALLY RULE", finallyWord.tag(finallyWordTag), expr)

  val finallyBlock = ScalaBlockCompositeRule(ScalaElementTypes.FINALLY_BLOCK, finallyRule, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "FINALLY BLOCK")

  val maybeCatchBlock = MaybeRule(catchBlock,
    IndentType.NormalIndent,
    "MAYBE CATCH BLOCK")

  val maybeFinallyBlock = MaybeRule(finallyBlock,
    IndentType.NormalIndent,
    "MAYBE FINALLY BLOCK"
  )

  val tryBlock = ScalaBlockCompositeRule(ScalaElementTypes.TRY_BLOCK, ScalaFormattingCompositeRule("TRY BLOCK COMPOSITE", tryWord, bracedExpr), ScalaFormattingRule.RULE_PRIORITY_DEFAULT, "TRY BLOCK")

  val tryComposite = ScalaFormattingCompositeRule("TRY COMPOSITE",
    tryBlock, maybeCatchBlock, maybeFinallyBlock)

  //now, define resulting rules

  val ifDefault = ScalaBlockCompositeRule(ScalaElementTypes.IF_STMT,
    ScalaFormattingCompositeRule("IF DEFAULT COMPOSITE", ifCompositeBlock, maybeElse),
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "IF DEFAULT")

  val whileDefault = ScalaBlockCompositeRule(ScalaElementTypes.WHILE_STMT,
    whileComposite,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "WHILE DEFAULT")

  val doWord = ScalaBlockRule("do", "DO WORD")

  val doDefault = block(
    ScalaElementTypes.DO_STMT,
    defaultPriority,
    seq(
      doWord,
      maybeBlockExpr,
      maybeSemi,
      whileWord,
      leftParenthesis,
      expr,
      rightParenthesis
    )
  )

  val forWord = ScalaBlockRule("for", "FOR WORD")

  val generator = block(
    ScalaElementTypes.GENERATOR,
    defaultPriority,
    seq(
      pattern,
      `<-`,
      maybeBlockExpr,
      maybeGuard
    )
  )

  val generatorAnchor = "GENERATOR ANCHOR"

//  val pattern = ScalaBlockRule("REFERENCE PATTERN", ScalaElementTypes.REFERENCE_PATTERN)

  val enumerator = block(
    ScalaElementTypes.ENUMERATOR,
    defaultPriority,
    or(
      generator.&(generatorAnchor),
      guard,
      seq(pattern, equalitySign, maybeBlockExpr)
    )
  )

  val enumerators = block(
    ScalaElementTypes.ENUMERATORS,
    defaultPriority,
    seq(
      generator.&(generatorAnchor, noSpacingId),
      seq(semi, enumerator).*
    )
  )

  val forDefault = block(
    ScalaElementTypes.FOR_STMT,
    defaultPriority,
    seq(
      forWord,
      or(
        seq(
          leftParenthesis,
          enumerators,
          rightParenthesis
        ),
        seq(
          leftBrace,
          enumerators,
          rightBrace
        )
      ),
      maybe(ScalaBlockRule("yield", "YIELD WORD")),
      maybeBlockExpr
    )
  )

  val tryDefault = ScalaBlockCompositeRule(ScalaElementTypes.TRY_STMT,
    tryComposite,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "TRY DEFAULT") //or maybe TRY_BLOCK ???

  val idChainAnchor = "ID CHAIN ANCHOR"
  val idChainArgsAnchor = "ID CHAIN ARGS ANCHOR"
  val idChainDotAnchor = "ID CHAIN DOR ANCHOR"

  val argsRule = ScalaBlockRule("ARGUMENTS OF FUNCTION", ScalaElementTypes.ARG_EXPRS)
  val idChainMaybeArgsRule = MaybeRule(argsRule.&(idChainArgsAnchor).tag(idChainArgsTag), "MAYBE ARGUMENTS OF FUNCTION")

  val idChainDefault = ScalaFormattingCompositeRule(
    "ID CHAIN DEFAULT",
    ScalaSomeRule(1,
      ScalaFormattingCompositeRule(
        "ID CHAIN HEAD COMPOSITE",
        idRule.&(idChainAnchor),
        idChainMaybeArgsRule,
        dot.&(idChainDotAnchor)), "ID CHAIN HEAD"
    ),
    idRule.&(idChainAnchor),
    idChainMaybeArgsRule
  ).tag(idChainTag)

//  val importWord = ScalaBlockRule("IMPORT WORD", ScalaElementTypes.IMPORT)
//
//  val importReference = ScalaBlockRule("IMPORT REFERENCE", ScalaElementTypes.REFERENCE)
//
//  val importChainDefault = block(
//    ScalaElementTypes.IMPORT_STMT,
//    defaultPriority,
//    seq(
//      importWord,
//      block(
//        ScalaElementTypes.IMPORT_EXPR,
//        defaultPriority,
//        seq(
//          importReference,
//          seq(
//            dot,
//            importReference
//          ).*
//        )
//      )
//    )
//  )

  val parameterAlignmentAnchor = "PARAMETER ALIGNMENT ANCHOR"

  val parametersList = ScalaFormattingCompositeRule(
    "PARAMETERS LIST",
    leftParenthesis,
    expr.&(parameterAlignmentAnchor, noSpacingId),
    ScalaSomeRule(
      0,
      ScalaFormattingCompositeRule(
        "PARAMETERS LIST INNER",
        comma,
        expr.&(parameterAlignmentAnchor)
      ),
      "HEAD ARGS"
    ),
    rightParenthesis
  ) //TODO: refine expression types used here

  val parametersDefault = ScalaBlockCompositeRule(
    ScalaElementTypes.ARG_EXPRS,
    parametersList,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    "PARAMETERS DEFAULT"
  )

  val typeRule = ScalaBlockRule("TYPE", TokenSets.TYPE_ELEMENTS_TOKEN_SET.getTypes.toList)

  val typeRuleParamListId = "TYPE RULE PARAM LIST ANCHOR"

  val typeParametersList = block(
    ScalaElementTypes.TYPE_ARGS,
    ScalaFormattingRule.RULE_PRIORITY_DEFAULT,
    seq(
      leftBracket,
      typeRule.&(typeRuleParamListId, noSpacingId),
      seq(comma, typeRule.&(typeRuleParamListId)).*,
      rightBracket
    )
  )

}
