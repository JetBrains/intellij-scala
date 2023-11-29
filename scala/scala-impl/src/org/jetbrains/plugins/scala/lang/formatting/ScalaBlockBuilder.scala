package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}

import java.util
import com.intellij.psi.tree._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlockBuilder._
import org.jetbrains.plugins.scala.lang.formatting.getDummyBlocksUtils._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.util.MultilineStringUtil
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import scala.annotation.tailrec

//noinspection RedundantDefaultArgument
final class ScalaBlockBuilder(
  parentBlock: ScalaBlock,
  settings: CodeStyleSettings,
  commonSettings: CommonCodeStyleSettings,
  scalaSettings: ScalaCodeStyleSettings
) extends ScalaBlockBuilderBase(
  parentBlock,
  settings,
  commonSettings,
  scalaSettings
) {

  def this(parentBlock: ScalaBlock) = {
    this(
      parentBlock,
      parentBlock.settings,
      parentBlock.settings.getCommonSettings(ScalaLanguage.INSTANCE),
      parentBlock.settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    )
  }

  // TODO: there are quite many unnecessary array allocations and copies, consider passing
  //  mutable buffer/list everywhere and measure the performance!
  def buildSubBlocks: util.ArrayList[Block] = {
    val firstNode = parentBlock.node
    val lastNode = parentBlock.lastNode
    if (ScalaDocBlockBuilder.isScalaDocNode(firstNode)) {
      val builder = new ScalaDocBlockBuilder(parentBlock, settings, commonSettings, scalaSettings)
      builder.buildSubBlocks
    }
    else if (lastNode != null)
      buildSubBlocksInner(firstNode, lastNode)
    else
      buildSubBlocksInner(firstNode)
  }

  def buildSubBlocksInner(node: ASTNode): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val nodePsi = node.getPsi
    nodePsi match {
      case _: ScValue | _: ScVariable if cs.ALIGN_GROUP_FIELD_DECLARATIONS =>
        subBlocks.addAll(getFieldGroupSubBlocks(node))
        return subBlocks
      case _: ScCaseClause if ss.ALIGN_IN_COLUMNS_CASE_BRANCH =>
        subBlocks.addAll(getCaseClauseGroupSubBlocks(node))
        return subBlocks
      case _: ScIf =>
        val alignment = if (ss.ALIGN_IF_ELSE) Alignment.createAlignment else null
        subBlocks.addAll(getIfSubBlocks(node, alignment))
        return subBlocks
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixTypeElement =>
        subBlocks.addAll(getInfixBlocks(node))
        return subBlocks
      case extendsBlock: ScExtendsBlock =>
        subBlocks.addAll(getExtendsSubBlocks(extendsBlock))
        return subBlocks
      case _: ScFor =>
        subBlocks.addAll(getForSubBlocks(node.getChildren(null)))
        return subBlocks
      case e if ChainedMethodCallsBlockBuilder.canContainMethodCallChain(e) =>
        val builder = new ChainedMethodCallsBlockBuilder(parentBlock, settings, cs, ss)
        val result = builder.buildSubBlocks(node)
        subBlocks.addAll(result)
        return subBlocks
      case _: ScLiteral if node.getFirstChildNode != null &&
        node.getFirstChildNode.getElementType == tMULTILINE_STRING && ss.supportMultilineString =>
        subBlocks.addAll(getMultilineStringBlocks(node))
        return subBlocks
      case pack: ScPackaging =>
        /** see [[ScPackaging.findExplicitMarker]] doc */
        val explicitMarker = pack.findExplicitMarker
        explicitMarker match {
          case Some(marker) =>
            val markerNode = marker.getNode
            val correctChildren = node.getChildren(null).filter(isNotEmptyNode)
            val (beforeMarker, afterMarker) = correctChildren.span(_ != markerNode)
            val hasValidTail = afterMarker.nonEmpty && (
              afterMarker.head.getElementType == tLBRACE && afterMarker.last.getElementType == tRBRACE ||
                afterMarker.head.getElementType == tCOLON
              )
            for (child <- if (hasValidTail) beforeMarker else correctChildren) {
              subBlocks.add(subBlock(child))
            }
            if (hasValidTail) {
              subBlocks.add(subBlock(afterMarker.head, afterMarker.last))
            }
            return subBlocks
          case _ =>
        }
      case interpolated: ScInterpolatedStringLiteral =>
        //create and store alignment; required for support of multi-line interpolated strings (SCL-8665)
        interpolated.putUserData(interpolatedStringAlignmentsKey, buildQuotesAndMarginAlignments)
      case paramClause: ScParameterClause =>
        paramClause.putUserData(typeParameterTypeAnnotationAlignmentsKey, Alignment.createAlignment(true))
      case psi@(_: ScValueOrVariable | _: ScFunction) if node.getFirstChildNode.getPsi.is[PsiComment] =>
        //Comments before definitions are attached to the definition element in parser (see org.jetbrains.plugins.scala.lang.parser.ScalaTokenBinders)
        //Here we unbind them and create separate block for such comments
        val childrenFiltered: Array[ASTNode] = node.getChildren(null).filter(isNotEmptyNode)
        val (leadingComments, otherChildren) = childrenFiltered.span(_.getPsi.is[PsiComment])
        leadingComments.map(subBlock(_)).foreach(subBlocks.add)
        val indent: Indent = {
          val prevNonWsNode: Option[PsiElement] = psi.prevSibling match {
            case Some(prev@Whitespace(s)) =>
              if (s.contains("\n")) None
              else prev.prevSibling
            case prev =>
              prev
          }
          prevNonWsNode.map(_.elementType) match {
            case Some(`tLBRACE` | `tLPARENTHESIS`) if scalaSettings.KEEP_COMMENTS_ON_SAME_LINE =>
              Indent.getNormalIndent
            case _ =>
              Indent.getNoneIndent
          }
        }
        subBlocks.add(subBlock(otherChildren.head, otherChildren.last, null, Some(indent)))
        return subBlocks
      case _ =>
    }

    val sharedAlignment: Alignment = createAlignment(node)

    val children = node.getChildren(null)
    for (child <- children if isNotEmptyNode(child)) {
      val childAlignment: Alignment = calcChildAlignment(node, child, sharedAlignment)

      val needFlattenInterpolatedStrings = child.getFirstChildNode == null &&
        child.getElementType == tINTERPOLATED_MULTILINE_STRING &&
        ss.supportMultilineString
      if (needFlattenInterpolatedStrings) {
        subBlocks.addAll(getMultilineStringBlocks(child))
      } else {
        subBlocks.add(subBlock(child, null, childAlignment))
      }
    }

    subBlocks
  }

  private def calcChildAlignment(parent: ASTNode, child: ASTNode, sharedAlignment: Alignment): Alignment =
    parent.getPsi match {
      case params: ScParameters                                         =>
        val firstParameterStartsFromNewLine =
          commonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE ||
            params.clauses.headOption.flatMap(_.parameters.headOption).forall(_.startsFromNewLine())
        if (firstParameterStartsFromNewLine && !scalaSettings.INDENT_FIRST_PARAMETER) null
        else sharedAlignment
      case _: ScParameterClause =>
        child.getElementType match {
          case `tRPARENTHESIS` | `tLPARENTHESIS` => null
          case _ => sharedAlignment
        }
      case _: ScArgumentExprList =>
        child.getElementType match {
          case `tRPARENTHESIS` if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => sharedAlignment
          case `tRPARENTHESIS` | `tLPARENTHESIS` => null
          case ScCodeBlockElementType.BlockExpression if ss.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
          case _ if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => sharedAlignment
          case _ => null
        }
      case patt: ScPatternArgumentList =>
        child.getElementType match {
          case `tRPARENTHESIS` if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS && patt.missedLastExpr => sharedAlignment
          case `tRPARENTHESIS` | `tLPARENTHESIS` => null
          case ScCodeBlockElementType.BlockExpression if ss.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
          case _ if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => sharedAlignment
          case _ => null
        }
      case _: ScMethodCall | _: ScReferenceExpression =>
        if (child.getElementType == tIDENTIFIER &&
          child.getPsi.getParent.is[ScReferenceExpression] &&
          child.getPsi.getParent.asInstanceOf[ScReferenceExpression].qualifier.isEmpty) null
        else if (child.getPsi.is[ScExpression]) null
        else sharedAlignment
      case _: ScXmlStartTag | _: ScXmlEmptyTag =>
        child.getElementType match {
          case ScalaElementType.XML_ATTRIBUTE => sharedAlignment
          case _ => null
        }
      case _: ScXmlElement =>
        child.getElementType match {
          case ScalaElementType.XML_START_TAG | ScalaElementType.XML_END_TAG => sharedAlignment
          case _ => null
        }
      case param: ScParameter =>
        import ScalaCodeStyleSettings._
        val addAlignmentToChild = ss.ALIGN_PARAMETER_TYPES_IN_MULTILINE_DECLARATIONS match {
          case ALIGN_ON_COLON => child.getElementType == tCOLON
          case ALIGN_ON_TYPE  => child.getElementType == ScalaElementType.PARAM_TYPE
          case _              => false
        }
        if (addAlignmentToChild) {
          val parameterClause = Option(PsiTreeUtil.getParentOfType(param, classOf[ScParameterClause], false))
          val alignmentOpt = parameterClause.flatMap(cachedParameterTypeAnnotationAlignment)
          alignmentOpt.getOrElse(sharedAlignment)
        }
        else sharedAlignment
      case literal: ScInterpolatedStringLiteral if child.getElementType == tINTERPOLATED_STRING_END =>
        cachedAlignment(literal).map(_.quotes).orNull
      case _ =>
        sharedAlignment
    }

  private def getCaseClauseGroupSubBlocks(node: ASTNode): util.ArrayList[Block] = {
    val children = node.getChildren(null).filter(isNotEmptyNode)
    val subBlocks = new util.ArrayList[Block]

    def getPrevGroupNode(nodePsi: PsiElement) = {
      var prev = nodePsi.getPrevSibling
      var breaks = 0

      def isOk(psi: PsiElement): Boolean = psi match {
        case _: ScCaseClause => true
        case _: PsiComment => false
        case _: PsiWhiteSpace =>
          breaks += psi.getText.count(_ == '\n')
          false
        case _ =>
          breaks += 2
          false
      }

      while (prev != null && breaks <= 1 && !isOk(prev)) {
        prev = prev.getPrevSibling
      }
      if (breaks == 1 && prev != null) prev.getNode
      else null
    }

    var prevChild: ASTNode = null
    for (child <- children) {
      val childAlignment = calcChildAlignment(node, child)(getPrevGroupNode)(FunctionTypeTokenSet)
      subBlocks.add(subBlock(child, null, childAlignment))
      prevChild = child
    }

    subBlocks
  }

  private def getFieldGroupSubBlocks(node: ASTNode): util.ArrayList[Block] = {
    val children = node.getChildren(null).filter(isNotEmptyNode)
    val subBlocks = new util.ArrayList[Block]

    def getPrevGroupNode(nodePsi: PsiElement) = {
      var prev = nodePsi.getPrevSibling
      var breaks = 0

      def isOk(psi: PsiElement): Boolean = psi match {
        case ElementType(t) if t == tSEMICOLON =>
          false
        case _: ScVariableDeclaration | _: ScValueDeclaration if nodePsi.is[ScPatternDefinition, ScVariableDefinition] =>
          breaks += 2
          false
        case _: ScVariableDefinition | _: ScPatternDefinition if nodePsi.is[ScValueDeclaration, ScValueDeclaration] =>
          breaks += 2
          false
        case _: ScVariable | _: ScValue =>
          def hasEmptyModifierList(psi: PsiElement): Boolean = psi match {
            case mod: ScModifierListOwner if mod.getModifierList.getTextLength == 0 => true
            case _ => false
          }

          if (hasEmptyModifierList(psi) != hasEmptyModifierList(nodePsi)) {
            breaks += 2
            false
          } else {
            true
          }
        case _: PsiComment =>
          false
        case _: PsiWhiteSpace =>
          breaks += psi.getText.count(_ == '\n')
          false
        case _ =>
          breaks += 2
          false
      }

      while (prev != null && breaks <= 1 && !isOk(prev)) {
        prev = prev.getPrevSibling
      }
      if (breaks == 1 && prev != null) prev.getNode
      else null
    }

    var prevChild: ASTNode = null
    for (child <- children) {
      //TODO process rare case of first-line comment before one of the fields  for SCL-10000 here
      val childAlignment = calcChildAlignment(node, child)(getPrevGroupNode)(FieldGroupSubBlocksTokenSet)
      subBlocks.add(subBlock(child, null, childAlignment))
      prevChild = child
    }
    subBlocks
  }

  @tailrec
  private def calcChildAlignment(node: ASTNode, child: ASTNode)
                                     (getPrevGroupNode: PsiElement => ASTNode)
                                     (implicit tokenSet: TokenSet): Alignment = {
    def createNewAlignment: Alignment = {
      val alignment = Alignment.createAlignment(true)
      child.getPsi.putUserData(fieldGroupAlignmentKey, alignment)
      alignment
    }

    val prev = getPrevGroupNode(node.getPsi)
    child.getElementType match {
      case elementType if tokenSet.contains(elementType) =>
        prev match {
          case null => createNewAlignment
          case _ =>
            prev.findChildByType(elementType) match {
              case null => calcChildAlignment(prev, child)(getPrevGroupNode)
              case prevChild =>
                val newAlignment = prevChild.getPsi.getUserData(fieldGroupAlignmentKey) match {
                  case null => createNewAlignment
                  case alignment => alignment
                }
                child.getPsi.putUserData(fieldGroupAlignmentKey, newAlignment)
                newAlignment
            }
        }
      case _ => null
    }
  }

  private def getExtendsSubBlocks(extBlock: ScExtendsBlock): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val firstChild = extBlock.getFirstChild
    if (firstChild == null) return subBlocks
    val tempBody = extBlock.templateBody

    val lastChild = tempBody.map(_.getPrevSiblingNotWhitespace).getOrElse(extBlock.getLastChild)
    if (lastChild != null) {
      val alignment =
        if (ss.ALIGN_EXTENDS_WITH == ScalaCodeStyleSettings.ALIGN_TO_EXTENDS) Alignment.createAlignment(false)
        else null
      subBlocks.add(subBlock(firstChild.getNode, lastChild.getNode, alignment))
    }

    tempBody match {
      case Some(x) =>
        subBlocks.add(subBlock(x.getNode))
      case _ =>
    }

    subBlocks
  }

  private def getForSubBlocks(children: Array[ASTNode]): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]()

    var prevChild: ASTNode = null
    def addTail(tail: List[ASTNode]): Unit = {
      for (child <- tail) {
        if (!isYieldOrDo(child))
          if (prevChild != null && isYieldOrDo(prevChild))
            subBlocks.add(subBlock(prevChild, child))
          else
            subBlocks.add(subBlock(child, null))
        prevChild = child
      }
      if (prevChild != null && isYieldOrDo(prevChild)) {
        //add a block for 'yield' in case of incomplete for statement (expression after yield is missing)
        subBlocks.add(subBlock(prevChild, null))
      }
    }

    @tailrec
    def addFor(children: List[ASTNode]): Unit = children match {
      case forWord :: tail if forWord.getElementType == kFOR =>
        subBlocks.add(subBlock(forWord, null))
        addFor(tail)
      case lParen :: tail if LBRACE_LPARENT_TOKEN_SET.contains(lParen.getElementType) =>
        val closingType =
          if (lParen.getElementType == tLPARENTHESIS) tRPARENTHESIS
          else tRBRACE
        val afterClosingParent = tail.dropWhile(_.getElementType != closingType)
        afterClosingParent match {
          case Nil =>
            addTail(children)
          case rParent :: yieldNodes =>
            val enumerators = tail.dropWhile(x => ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(x.getElementType)).head
            val context = if (commonSettings.ALIGN_MULTILINE_FOR && !enumerators.getPsi.startsFromNewLine()) {
              val alignment = Alignment.createAlignment()
              Some(SubBlocksContext.withChildrenAlignments(Map(rParent -> alignment, enumerators -> alignment)))
            } else {
              None
            }
            subBlocks.add(subBlock(lParen, rParent, context = context))
            addTail(yieldNodes)
        }
      case _ =>
        addTail(children)
    }
    addFor(children.filter(isNotEmptyNode).toList)

    subBlocks
  }

  private def getIfSubBlocks(node: ASTNode, alignment: Alignment): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val ifAndThenBlockFirstNode = node.getFirstChildNode // `if`
    val ifAndThenBlockLastNode = ifAndThenBlockFirstNode
      .treeNextNodes
      .takeWhile(_.getElementType != kELSE)
      .filterNot(_.isInstanceOf[PsiWhiteSpace])
      .lastOption
      .getOrElse(ifAndThenBlockFirstNode)

    val firstBlock = subBlock(ifAndThenBlockFirstNode, ifAndThenBlockLastNode, alignment)
    subBlocks.add(firstBlock)

    val elseBlockFirstNodeOpt = ifAndThenBlockLastNode.treeNextNodes.dropWhile(_.isInstanceOf[PsiWhiteSpace]).nextOption()

    elseBlockFirstNodeOpt match {
      case Some(elseBlockFirstNode) =>
        val elseLastNode = elseBlockFirstNode.treeNextNodes
          .filterNot(_.isInstanceOf[PsiWhiteSpace])
          .takeWhile(n => if (cs.SPECIAL_ELSE_IF_TREATMENT) n.getElementType != kIF else true)
          .lastOption
          .getOrElse(elseBlockFirstNode)

        subBlocks.add(subBlock(elseBlockFirstNode, elseLastNode, alignment, Some(firstBlock.indent)))
        val next = elseLastNode.getTreeNext
        if (next != null && next.getElementType == kIF) {
          subBlocks.addAll(getIfSubBlocks(next, alignment))
        }
      case None =>
    }

    subBlocks
  }

  private def interpolatedRefLength(node: ASTNode): Int =
    if (node.getElementType == tINTERPOLATED_MULTILINE_STRING) {
      node.getPsi.getParent match {
        case str: ScInterpolatedStringLiteral => str.referenceName.length
        case _ => 0
      }
    } else 0

  private def buildQuotesAndMarginAlignments: InterpolatedStringAlignments = {
    val quotesAlignment = if (scalaSettings.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES) Alignment.createAlignment() else null
    val marginAlignment = Alignment.createAlignment(true)
    InterpolatedStringAlignments(quotesAlignment, marginAlignment)
  }

  private def getMultilineStringBlocks(node: ASTNode): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val interpolatedOpt = Option(PsiTreeUtil.getParentOfType(node.getPsi, classOf[ScInterpolatedStringLiteral]))
    val InterpolatedStringAlignments(quotesAlignment, marginAlignment) =
      interpolatedOpt
        .flatMap(cachedAlignment)
        .getOrElse(buildQuotesAndMarginAlignments)

    val wrap = Wrap.createWrap(WrapType.NONE, true)
    val marginChar = MultilineStringUtil.getMarginChar(node.getPsi)
    val marginIndent = Indent.getSpaceIndent(ss.MULTILINE_STRING_MARGIN_INDENT + interpolatedRefLength(node), true)

    def relativeRange(start: Int, end: Int, shift: Int = 0): TextRange =
      TextRange.from(node.getStartOffset + shift + start, end - start)

    val lines = node.getText.split("\n")
    var acc = 0
    lines.foreach { line =>
      val trimmedLine = line.trim()
      val lineLength = line.length
      val linePrefixLength = if (settings.useTabCharacter(ScalaFileType.INSTANCE)) {
        val tabsCount = line.segmentLength(_ == '\t')
        tabsCount + line.substring(tabsCount).segmentLength(_ == ' ')
      } else {
        line.segmentLength(_ == ' ')
      }

      if (trimmedLine.startsWith(marginChar)) {
        val marginRange = relativeRange(linePrefixLength, linePrefixLength + 1, acc)
        subBlocks.add(new StringLineScalaBlock(marginRange, node, marginAlignment, marginIndent, null, settings))
        val contentRange = relativeRange(linePrefixLength + 1, lineLength, acc)
        subBlocks.add(new StringLineScalaBlock(contentRange, node, null, Indent.getNoneIndent, wrap, settings))
      } else if (trimmedLine.nonEmpty) {
        val (range, myIndent, myAlignment) =
          if (trimmedLine.startsWith(MultilineQuotes)) {
            if (acc == 0) {
              val hasMarginOnFirstLine = trimmedLine.charAt(MultilineQuotes.length.min(trimmedLine.length - 1)) == '|'
              if (hasMarginOnFirstLine && lineLength > 3) {
                val range = relativeRange(0, 3)
                val marginBlock = new StringLineScalaBlock(range, node, quotesAlignment, Indent.getNoneIndent, null, settings)
                subBlocks.add(marginBlock)
                //now, return block parameters for text after the opening quotes
                (relativeRange(3, lineLength), Indent.getNoneIndent, marginAlignment)
              } else {
                (relativeRange(linePrefixLength, lineLength), Indent.getNoneIndent, quotesAlignment)
              }
            } else {
              (relativeRange(linePrefixLength, lineLength, acc), Indent.getNoneIndent, quotesAlignment)
            }
          } else {
            (relativeRange(0, lineLength, acc), Indent.getAbsoluteNoneIndent, null)
          }
        subBlocks.add(new StringLineScalaBlock(range, node, myAlignment, myIndent, null, settings))
      }

      acc += lineLength + 1
    }

    subBlocks
  }

  private def getInfixBlocks(node: ASTNode, parentAlignment: Alignment = null): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]
    val children = node.getChildren(null)
    val alignment =
      if (parentAlignment != null) parentAlignment
      else createAlignment(node)
    for (child <- children) {
      if (InfixElementsTokenSet.contains(child.getElementType) && infixPriority(node) == infixPriority(child)) {
        subBlocks.addAll(getInfixBlocks(child, alignment))
      } else if (isNotEmptyNode(child)) {
        subBlocks.add(subBlock(child, null, alignment))
      }
    }
    subBlocks
  }

  private def createAlignment(node: ASTNode): Alignment = {
    import Alignment.{createAlignment => create}
    import commonSettings._
    node.getPsi match {
      case _: ScXmlStartTag                                                          => create //todo:
      case _: ScXmlEmptyTag                                                          => create //todo:
      case _: ScParameters if ALIGN_MULTILINE_PARAMETERS                             => create
      case _: ScParameterClause if ALIGN_MULTILINE_PARAMETERS                        => create
      case _: ScArgumentExprList if ALIGN_MULTILINE_PARAMETERS_IN_CALLS              => create
      case _: ScPatternArgumentList if ALIGN_MULTILINE_PARAMETERS_IN_CALLS           => create
      case _: ScEnumerators if ALIGN_MULTILINE_FOR                                   => create
      case _: ScParenthesisedExpr if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION        => create
      case _: ScParenthesisedTypeElement if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => create
      case _: ScParenthesisedPattern if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION     => create
      case _: ScInfixExpr if ALIGN_MULTILINE_BINARY_OPERATION                        => create
      case _: ScInfixPattern if ALIGN_MULTILINE_BINARY_OPERATION                     => create
      case _: ScInfixTypeElement if ALIGN_MULTILINE_BINARY_OPERATION                 => create
      case _: ScCompositePattern if ss.ALIGN_COMPOSITE_PATTERN                       => create
      case _                                                                         => null
    }
  }

  private def buildSubBlocksInner(node: ASTNode, lastNode: ASTNode): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    def getChildBlock(child: ASTNode): ScalaBlock = {
      val lastNode = parentBlock.getChildBlockLastNode(child)
      val alignment = parentBlock.getChildBlockCustomAlignment(child).orNull
      val context = parentBlock.getChildBlockContext(child)
      subBlock(child, lastNode, alignment, context = context)
    }

    var child: ASTNode = node
    do {
      if (isNotEmptyNode(child)) {
        if (child.getPsi.is[ScTemplateParents]) {
          subBlocks.addAll(getTemplateParentsBlocks(child))
        } else {
          subBlocks.add(getChildBlock(child))
        }
      }
    } while (child != lastNode && {
      child = child.getTreeNext
      child != null
    })

    //it is not used right now, but could come in handy later
    for {
      context <- parentBlock.subBlocksContext
      additionalNode <- context.additionalNodes
    } {
      val childBlock = getChildBlock(additionalNode)
      subBlocks.add(childBlock)
    }

    subBlocks
  }

  //class A() extends B() with T1 with T2
  //                  |----this part-----|
  private def getTemplateParentsBlocks(node: ASTNode): util.List[Block] = {
    val subBlocks = new util.ArrayList[Block]

    import ScalaCodeStyleSettings._
    val alignSetting = ss.ALIGN_EXTENDS_WITH
    val alignment =
      if (alignSetting == ALIGN_TO_EXTENDS) parentBlock.getAlignment
      else Alignment.createAlignment(true)

    val children = node.getChildren(null)
    for (child <- children if isNotEmptyNode(child)) {
      val actualAlignment = (child.getElementType, alignSetting) match {
        case (_, DO_NOT_ALIGN) => null
        case (`kWITH` | `kEXTENDS`, ON_FIRST_ANCESTOR) => null
        case _ => alignment
      }
      val lastNode = parentBlock.getChildBlockLastNode(child)
      val context = parentBlock.getChildBlockContext(child)
      subBlocks.add(subBlock(child, lastNode, actualAlignment, context = context))
    }
    subBlocks
  }
}

object ScalaBlockBuilder {

  private case class InterpolatedStringAlignments(quotes: Alignment, marginChar: Alignment)
  private val interpolatedStringAlignmentsKey: Key[InterpolatedStringAlignments] = Key.create("interpolated.string.alignment")
  /** the alignment can be applied both to the colon and type annotation itself, depending on ScalaCodeStyleSettings.ALIGN_PARAMETER_TYPES_IN_MULTILINE_DECLARATIONS  */
  private val typeParameterTypeAnnotationAlignmentsKey: Key[Alignment] = Key.create("colon.in.type.annotation.alignments.key")

  private val fieldGroupAlignmentKey: Key[Alignment] = Key.create("field.group.alignment.key")

  private val InfixElementsTokenSet = TokenSet.create(
    ScalaElementType.INFIX_EXPR,
    ScalaElementType.INFIX_PATTERN,
    ScalaElementType.INFIX_TYPE
  )

  private val FieldGroupSubBlocksTokenSet = TokenSet.orSet(
    TokenSet.create(tCOLON, tASSIGN),
    VAL_VAR_TOKEN_SET
  )

  private val FunctionTypeTokenSet = TokenSet.create(
    tFUNTYPE,
    tFUNTYPE_ASCII
  )

  private def cachedAlignment(literal: ScInterpolatedStringLiteral): Option[InterpolatedStringAlignments] =
    Option(literal.getUserData(interpolatedStringAlignmentsKey))

  private def cachedParameterTypeAnnotationAlignment(clause: ScParameterClause): Option[Alignment] =
    Option(clause.getUserData(typeParameterTypeAnnotationAlignmentsKey))

  private def infixPriority(node: ASTNode): Int = node.getPsi match {
    case inf: ScInfixExpr => ParserUtils.priority(inf.operation.getText, assignments = true)
    case inf: ScInfixPattern => ParserUtils.priority(inf.operation.getText, assignments = false)
    case inf: ScInfixTypeElement => ParserUtils.priority(inf.operation.getText, assignments = false)
    case _ => 0
  }
}
