package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.ChainedMethodCallsBlockBuilder._
import org.jetbrains.plugins.scala.lang.formatting.ScalaDocBlockBuilderUtils._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaIndentProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMatchImpl

import java.util
import scala.annotation.tailrec

private final class ChainedMethodCallsBlockBuilder(
  parentBlock: ScalaBlock,
  settings: CodeStyleSettings,
  commonSettings: CommonCodeStyleSettings,
  scalaSettings: ScalaCodeStyleSettings
) extends ScalaBlockBuilderBase(parentBlock, settings, commonSettings, scalaSettings) {

  private val chainAlignment = if (cs.ALIGN_MULTILINE_CHAINED_METHODS) Alignment.createAlignment() else null
  private val chainWrap = parentBlock.suggestedWrap
  private val smartIndent: Indent = Indent.getSmartIndent(Indent.Type.CONTINUATION, false)

  def buildSubBlocks(node: ASTNode): util.List[ScalaBlock] = {
    val result = new util.ArrayList[ScalaBlock]

    collectChainedMethodCalls(node)(result)

    //We need to sort blocks because we add them in arbitrary order
    util.Collections.sort(result, util.Comparator.comparingInt[ScalaBlock](_.node.getTextRange.getStartOffset))

    adjustIndentationForBlocksWithArgumentsWithColonSyntax(result)

    result
  }

  private def adjustIndentationForBlocksWithArgumentsWithColonSyntax(chainBlocks: util.ArrayList[ScalaBlock]): Unit = {
    val noneIndent = Indent.getNoneIndent

    /**
     * `true` if before current block there was at least one block like `.bar: 42` from this example {{{
     *   foo.bar:
     *       42
     *   .baz(42)
     * }}}
     * In this case we must not indent following chain blocks.
     * Otherwise, if we add extra indent format it like this: {{{
     *   foo.bar:
     *           42
     *       .baz(42)
     * }}}
     * scala compiler will generate error "The start of this line does not match any of the previous indentation widths"
     */
    var chainHasBlockWithColonArgStartingNotFromNewLineBeforeCurrent = false
    //First block in method chain is special (see comment in Scala3FormatterMethodCallChainWithArgumentsWithColonSyntaxTest.testOnlyBracesSyntax2)
    var chainFirstBlockHasColonArgSyntax = false
    //tracks the presence of a line break in the chain before the current block
    var chainHasLineBreakBefore = false

    var doNotIndentRemainingChainBlocks = false

    //using imperative style: during many experiments with implementation of this method
    // `while` proved to be most readable and agile (it's easier to quickly change the implementation)
    var idx = 0
    while (idx < chainBlocks.size()) {
      val currentBlock = chainBlocks.get(idx)
      val currentBlockStartsFromNewLine = currentBlock.getNode.getPsi.startsFromNewLine()

      doNotIndentRemainingChainBlocks |= chainFirstBlockHasColonArgSyntax || {
        !scalaSettings.INDENT_FEWER_BRACES_IN_METHOD_CALL_CHAINS &&
          chainHasBlockWithColonArgStartingNotFromNewLineBeforeCurrent &&
          currentBlockStartsFromNewLine
      }

      if (doNotIndentRemainingChainBlocks) {
        currentBlock.indent = noneIndent
      }
      else {
        val isFirstBlock = idx == 0
        if (currentBlockStartsFromNewLine && !isFirstBlock) {
          chainHasLineBreakBefore = true
        }

        val currentBlockEndsWithColonArgs = isBlockWithColonArgInTheEnd(currentBlock)
        if (currentBlockEndsWithColonArgs) {
          if (!chainHasLineBreakBefore) {
            chainHasBlockWithColonArgStartingNotFromNewLineBeforeCurrent = true
          }
          if (isFirstBlock) {
            chainFirstBlockHasColonArgSyntax = true
          }
        }
      }

      idx += 1
    }
  }

  @inline private def isBlockWithColonArgInTheEnd(block: ScalaBlock): Boolean = block match {
    case block: ChainedMethodCallBlock => block.endsWithColonArgsOrBraceOrIndentedCaseClauses
    case _ => false
  }

  /**
   * @param delegatedChildren can contain arguments, type arguments and comments between them
   */
  @tailrec
  private def collectChainedMethodCalls(
    node: ASTNode,
    dotIsFollowedByNewLine: Boolean = false,
    delegatedChildren: List[ASTNode] = List(),
    delegatedContext: Map[ASTNode, SubBlocksContext] = Map(),
  )(implicit result: java.util.List[ScalaBlock]): Unit = {
    val psi = node.getPsi
    if (canContainMethodCallChain(psi) || psi.is[ScGenericCall]) {
      //continue
    }
    else {
      result.add(subBlock(node))
      for (child <- delegatedChildren.filter(isNotEmptyNode)) {
        result.add(subBlock(child))
      }
      return
    }

    val childrenAll = node.getChildren(null)
    val childrenNonEmpty = childrenAll.filter(isNotEmptyNode).toList

    /**
     * some edge cases with comments in the middle of a method call: {{{
     *   Seq(1) // comment
     *     .map(_ * 2)
     *
     *   foo // comment
     *   {}
     * }}}
     */
    val (comments, children) = childrenNonEmpty.partition(isComment)

    lazy val delegatedChildrenSorted = sortByStartOffset(delegatedChildren)

    lazy val delegatedChildrenNotAlreadyInSomeContext = {
      // using Set we imply that ASTNode equals and hashCode methods are lightweight (default implementation)
      val filterOutNodes = delegatedContext.values.flatMap(_.additionalNodes).toSet
      delegatedChildrenSorted.filterNot(filterOutNodes.contains)
    }

    //Example of recursive descent:
    //value.method1.method2[String](1, 2, 3)
    //|---------------------------||-------|
    //|-------------------||------||~~~~~~~| delegated: args `(1, 2, 3)`
    //|-----------|.|-----||~~~~~~||~~~~~~~| delegated: args `(1, 2, 3)` and type args `[String]`
    //|---|.|-----|.|-----||~~~~~~||~~~~~~~| delegated: args `(1, 2, 3)` and type args `[String]`
    //
    //dot can be null in Scala 3, see SCL-22238
    def `add blocks for "expr.xxx"`(expr: ASTNode, @Nullable dot: ASTNode, nodeAfterDot: ASTNode): Boolean = {
      //NOTE: we shadow `dotFollowedByNewLine` parameter, cause here we are interested in the new dot
      val dotIsFollowedByNewLine = dot != null && dot.getPsi.followedByNewLine()

      val splitAtNode = if (dotIsFollowedByNewLine) nodeAfterDot else dot

      assert(childrenNonEmpty.head.eq(expr), "assuming that first child is expr and comments can't go before it")
      val (nodesOnPrevLine, nodesOnNextLine) = childrenNonEmpty.tail.span(c => {
        //if chain part starts with a comment, include it into block:
        //value
        //  /*comment*/.map(x => x)
        val split = (c eq splitAtNode) || c.getPsi.startsFromNewLine(false)
        !split
      })

      //add whatever goes on previous line to a separate block
      //Example 1 (here `//comment` goes to separate block)
      //  seq //comment
      //    .map(x => x)
      //Example 2 (here `. //comment` goes to separate block)
      //  seq.//comment
      //    map(x => x)
      val chainCallIsSplitToTwoBlocks = nodesOnPrevLine.nonEmpty && nodesOnNextLine.nonEmpty
      if (chainCallIsSplitToTwoBlocks) {
        result.add(chainSubBlock(
          nodesOnPrevLine.head,
          nodesOnPrevLine.lastOption,
          None,
          Some(Indent.getContinuationIndent),
          Some(null),
          None
        ))
      }

      val context = SubBlocksContext.withChild(nodeAfterDot, delegatedChildrenNotAlreadyInSomeContext, None, delegatedContext)
      val nodes = (if (nodesOnNextLine.nonEmpty) nodesOnNextLine else nodesOnPrevLine) ++ delegatedChildrenSorted
      result.add(chainSubBlock(
        nodes.head,
        nodes.lastOption,
        Some(chainAlignment),
        Some(smartIndent),
        Some(chainWrap),
        Some(context)
      ))
      dotIsFollowedByNewLine
    }

    children match {
      case expr :: Nil =>
        val actualAlignment = if (dotIsFollowedByNewLine) chainAlignment else null
        val context = SubBlocksContext.withChild(expr, delegatedChildrenNotAlreadyInSomeContext, None, delegatedContext)
        result.add(chainSubBlock(
          expr,
          delegatedChildrenSorted.lastOption,
          Some(actualAlignment),
          context = Some(context)
        ))

      //caller(args)
      //expr.method1[String](1, 2, 3).method2[Int, String](4, 5, 6)
      //|--------------------caller----------------------||-args--|
      case caller :: args :: Nil if args.getElementType == ScalaElementType.ARG_EXPRS =>
        //TODO (minor) we ask `isInScala3File` for many elements, which is not optimal (it requires tree traversal to parent every time)
        // ideally we need to store information `isScala3` somewhere in global context when constructing blocks for entire scala file
        // (see other places using isInScala3File in formatter package)
        val argsPsi = args.getPsi
        if (argsPsi.startsFromNewLine() && argsPsi.isInScala3File) {
          //See SCL-22238 this code:
          //   List(1, 2, 3)
          //     .map(x => x + 42)
          //     (23)
          // is equivalent to
          //    List(1, 2, 3)
          //     .map(x => x + 42)
          //     .apply(23)
          // so (23) should go in a separate block as if it's .apply(23)
          val dotIsFollowedByNewLine = `add blocks for "expr.xxx"`(caller, null, args)
          collectChainedMethodCalls(caller, dotIsFollowedByNewLine)
        } else {
          val delegatedChildrenNew = args :: delegatedChildren ++ comments
          collectChainedMethodCalls(caller, dotIsFollowedByNewLine, delegatedChildrenNew, delegatedContext)
        }

      //caller[typeArgs]
      //expr.method1[String](1, 2, 3).method2[Int, String](4, 5, 6)
      //|--------------caller---------------||-typeArgs--|
      case caller :: typeArgs :: Nil if typeArgs.getElementType == ScalaElementType.TYPE_ARGS =>
        val delegatedChildrenNew = typeArgs :: delegatedChildren ++ comments
        collectChainedMethodCalls(caller, dotIsFollowedByNewLine, delegatedChildrenNew, Map(typeArgs -> new SubBlocksContext(sortByStartOffset(delegatedChildren))))

      //expr.method1[String](1, 2, 3).method2[Int, String](4, 5, 6)
      //|------------expr-----------|.|-id--|
      case expr :: dot :: id :: Nil if dot.getElementType == tDOT =>
        val dotIsFollowedByNewLine = `add blocks for "expr.xxx"`(expr, dot, id)
        collectChainedMethodCalls(expr, dotIsFollowedByNewLine)

      //handle `expr.match{...}` (match expression with dot is a new syntax in Scala 3)
      case expr :: dot :: matchKeyword :: _ if dot.getElementType == tDOT && matchKeyword.getElementType == kMATCH =>
        val dotIsFollowedByNewLine = `add blocks for "expr.xxx"`(expr, dot, matchKeyword)
        collectChainedMethodCalls(expr, dotIsFollowedByNewLine)
      case _ =>
        //NOTE: this branch is generally not expected, but don't ignore children if there are some left
        val childrenWithDelegated = children ++ delegatedChildren
        for (child <- childrenWithDelegated.filter(isNotEmptyNode)) {
          result.add(subBlock(child))
        }
    }
  }

  private def chainSubBlock(
    node: ASTNode,
    lastNode: Option[ASTNode],
    alignment: Option[Alignment] = None,
    indent: Option[Indent] = None,
    wrap: Option[Wrap] = None,
    context: Option[SubBlocksContext] = None
  ): ScalaBlock = {
    val isInMatchExpr = lastNode.exists { _.getTreeParent.getElementType == ScalaElementType.MATCH_STMT }
    val endsWithColonArgs = lastNode.map(_.getPsi).exists {
      case args: ScArgumentExprList => args.isColonArgs
      case _: ScCaseClauses => isInMatchExpr //if case clauses is the last node it means that there are no braces in the match statement
      case _ => false
    }
    val indentFinal = indent.getOrElse(ScalaIndentProcessor.getChildIndent(parentBlock, node))
    val wrapFinal = wrap.getOrElse(ScalaWrapManager.arrangeSuggestedWrapForChild(parentBlock, node, parentBlock.suggestedWrap)(scalaSettings))
    new ChainedMethodCallBlock(parentBlock, node, lastNode.orNull, alignment.orNull, indentFinal, wrapFinal, settings, context, endsWithColonArgs, isInMatchExpr)
  }
}


object ChainedMethodCallsBlockBuilder {
  @inline
  private def sortByStartOffset(nodes: Seq[ASTNode]): Seq[ASTNode] = nodes.sortBy(_.getTextRange.getStartOffset)

  def canContainMethodCallChain(psi: PsiElement): Boolean =
    psi match {
      case _: ScReferenceExpression |
           _: ScThisReference |
           _: ScSuperReference |
           _: ScMethodCall => true
      case m: ScMatchImpl =>
        //handle `expr.match {...}`
        val hasDot = Option(m.getFirstChild.getNextSiblingNotWhitespaceComment).exists(_.elementType == ScalaTokenTypes.tDOT)
        hasDot
      case _ => false
    }
}
