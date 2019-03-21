package org.jetbrains.plugins.scala
package lang
package formatting

import java.util

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.tree._
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.ScalaWrapManager._
import org.jetbrains.plugins.scala.lang.formatting.getDummyBlocks._
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// TODO: rename it to some Builder/Producer/etc...
object getDummyBlocks {
  private type InterpolatedPointer = SmartPsiElementPointer[ScInterpolatedStringLiteral]
  private val alignmentsMapKey: Key[mutable.Map[InterpolatedPointer, Alignment]] = Key.create("alingnments.map")
  private val fieldGroupAlignmentKey: Key[Alignment] = Key.create("field.group.alignment.key")
  private val multiLevelAlignmentKey: Key[mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]]] = Key.create("multilevel.alignment")

  private val InfixElementsTokenSet = TokenSet.create(
    ScalaElementType.INFIX_EXPR,
    ScalaElementType.INFIX_PATTERN,
    ScalaElementType.INFIX_TYPE
  )

  private val FieldGroupSubBlocksTokenSets = Seq(
    TokenSet.create(ScalaTokenTypes.tCOLON),
    TokenSet.create(ScalaTokenTypes.tASSIGN),
    ScalaTokenTypes.VAL_VAR_TOKEN_SET,
  )

  private val MethodCallOrReferenceTokenSet = TokenSet.create(
    ScalaElementType.METHOD_CALL,
    ScalaElementType.REFERENCE_EXPRESSION
  )

  def apply(settings: CodeStyleSettings): getDummyBlocks = new getDummyBlocks(settings)

  private def alignmentsMap(project: Project): mutable.Map[InterpolatedPointer, Alignment] = {
    project.getOrUpdateUserData(alignmentsMapKey, mutable.Map[InterpolatedPointer, Alignment]())
  }

  private def cachedAlignment(literal: ScInterpolatedStringLiteral): Option[Alignment] = {
    alignmentsMap(literal.getProject).collectFirst {
      case (pointer, alignment) if pointer.getElement == literal => alignment
    }
  }

  private def multiLevelAlignmentMap(project: Project): mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]] = {
    project.getOrUpdateUserData(multiLevelAlignmentKey, mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]]())
  }

  private def isCorrectBlock(node: ASTNode): Boolean = {
    StringUtils.isNotBlank(node.getChars)
  }

  private class StringLineScalaBlock(myTextRange: TextRange, mainNode: ASTNode, myParentBlock: ScalaBlock,
                                     myAlignment: Alignment, myIndent: Indent, myWrap: Wrap, mySettings: CodeStyleSettings)
    extends ScalaBlock(myParentBlock, mainNode, null, myAlignment, myIndent, myWrap, mySettings) {

    override def getTextRange: TextRange = myTextRange
    override def isLeaf = true
    override def isLeaf(node: ASTNode): Boolean = true
    override def getChildAttributes(newChildIndex: Int): ChildAttributes = new ChildAttributes(Indent.getNoneIndent, null)
    override def getSpacing(child1: Block, child2: Block): Spacing = Spacing.getReadOnlySpacing
    override def getSubBlocks: util.List[Block] = {
      if (subBlocks == null) {
        subBlocks = new util.ArrayList[Block]()
      }
      subBlocks
    }
  }
}

//noinspection RedundantDefaultArgument
class getDummyBlocks(private val settings: CodeStyleSettings) {
  private val commonSettings: CommonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)
  private implicit val scalaSettings: ScalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  private def cs = commonSettings
  private def ss = scalaSettings

  def apply(firstNode: ASTNode, lastNode: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    if (lastNode != null) {
      applyInner(firstNode, lastNode, block)
    } else {
      applyInner(firstNode, block)
    }
  }

  private def applyInner(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]

    node.getPsi match {
      case _: ScValue | _: ScVariable if cs.ALIGN_GROUP_FIELD_DECLARATIONS =>
        if (node.getTreeParent.getPsi match {
          case _: ScEarlyDefinitions | _: ScTemplateBody => true
          case _ => false
        }) {
          subBlocks.addAll(getFieldGroupSubBlocks(node, block))
          return subBlocks
        }
      case _: ScCaseClause if ss.ALIGN_IN_COLUMNS_CASE_BRANCH =>
        subBlocks.addAll(getCaseClauseGroupSubBlocks(node, block))
        return subBlocks
      case _: ScIf =>
        val alignment = if (ss.ALIGN_IF_ELSE) Alignment.createAlignment
        else null
        subBlocks.addAll(getIfSubBlocks(node, block, alignment))
        return subBlocks
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixTypeElement =>
        subBlocks.addAll(getInfixBlocks(node, block))
        return subBlocks
      case extendsBlock: ScExtendsBlock =>
        subBlocks.addAll(getExtendsSubBlocks(node, block, extendsBlock))
        return subBlocks
      case _: ScFor =>
        subBlocks.addAll(getForSubBlocks(node, block, children))
        return subBlocks
      case _: ScReferenceExpression | _: ScThisReference | _: ScSuperReference =>
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      case _: ScMethodCall =>
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      case _: ScLiteral if node.getFirstChildNode != null &&
        node.getFirstChildNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING &&
        ss.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE =>
        subBlocks.addAll(getMultilineStringBlocks(node, block))
        return subBlocks
      case _: ScTryBlock if children.headOption.exists(_.getElementType == ScalaTokenTypes.kTRY) =>
        //add try block
        subBlocks.add(getSubBlock(block, children.head))
        //add try expression block
        val tail = children.filter(isCorrectBlock).tail
        if (tail.nonEmpty) {
          val singleExpressionUnderTry = tail.length == 1 && tail.head.isInstanceOf[ScExpression]
          val tailBlock = if (singleExpressionUnderTry) {
            getSubBlock(block, tail.head)
          } else {
            getSubBlock(block, tail.head, tail.last)
          }
          subBlocks.add(tailBlock)
        }
        return subBlocks
      case pack: ScPackaging if pack.isExplicit =>
        val correctChildren = children.filter(isCorrectBlock)
        val (beforeOpenBrace, afterOpenBrace) = correctChildren.span(_.getElementType != ScalaTokenTypes.tLBRACE)
        val hasValidTail = afterOpenBrace.nonEmpty && afterOpenBrace.head.getElementType == ScalaTokenTypes.tLBRACE &&
          afterOpenBrace.last.getElementType == ScalaTokenTypes.tRBRACE
        for (child <- if (hasValidTail) beforeOpenBrace else correctChildren) {
          subBlocks.add(getSubBlock(block, child))
        }
        if (hasValidTail) {
          subBlocks.add(getSubBlock(block, afterOpenBrace.head, afterOpenBrace.last))
        }
        return subBlocks
      case _: ScDocComment =>
        var scalaDocPrevChildTag: Option[String] = None
        var contextAlignment: Alignment = Alignment.createAlignment(true)
        val alignment = if (mustAlignment(node)) Alignment.createAlignment else null
        for (child <- children if isCorrectBlock(child)) {
          val context = (child.getElementType match {
            case ScalaDocElementTypes.DOC_TAG =>
              val currentTag = Option(child.getFirstChildNode).filter(_.getElementType == ScalaDocTokenType.DOC_TAG_NAME).map(_.getText)
              if (scalaDocPrevChildTag.isEmpty || scalaDocPrevChildTag != currentTag) {
                contextAlignment = Alignment.createAlignment(true)
              }
              scalaDocPrevChildTag = currentTag
              Some(contextAlignment)
            case _ => None
          }).map(a => new SubBlocksContext(alignment = Some(a)))
          subBlocks.add(getSubBlock(block, child, null, alignment, context = context))
        }
        return subBlocks
      case _ if node.getElementType == ScalaDocElementTypes.DOC_TAG =>
        val docTag = node.getPsi.asInstanceOf[ScDocTag]

        @tailrec
        def getNonWsSiblings(firstNode: ASTNode, acc: List[ASTNode] = List()): List[ASTNode] =
          if (firstNode == null) {
            acc.reverse
          } else if (ScalaDocNewlinedPreFormatProcessor.isWhiteSpace(firstNode)) {
            getNonWsSiblings(firstNode.getTreeNext, acc)
          } else {
            getNonWsSiblings(firstNode.getTreeNext, firstNode :: acc)
          }

        val childBlocks = getNonWsSiblings(docTag.getFirstChild.getNode)
        //TODO whitespace between tag name and tag parameter (like in @param x) has type "DOC_COMMENT_DATA"
        //while it should be DOC_WHITESPACE
        childBlocks match {
          case tagName :: space :: tagParameter :: tail
            if Option(docTag.getValueElement).map(_.getNode).contains(tagParameter) =>
            subBlocks.add(getSubBlock(block, tagName))
            subBlocks.add(getSubBlock(block, space))
            subBlocks.add(getSubBlock(block, tagParameter, tail.lastOption.orNull))
          case tagName :: tail if Option(docTag.getNameElement).map(_.getNode).contains(tagName) =>
            subBlocks.add(getSubBlock(block, tagName))
            if (tail.nonEmpty) {
              if (tail.head.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)
                subBlocks.add(getSubBlock(block, tail.head, tail.last))
              else for (child <- tail) {
                subBlocks.add(getSubBlock(block, child))
              }
            }
          case _ =>
        }
        return subBlocks
      case interpolated: ScInterpolatedStringLiteral =>
        //create and store alignment; required for support of multi-line interpolated strings (SCL-8665)
        alignmentsMap(interpolated.getProject).put(interpolated.createSmartPointer, Alignment.createAlignment())
      case psi@(_: ScValueOrVariable | _: ScFunction) if node.getFirstChildNode.getPsi.isInstanceOf[PsiComment] =>
        val childrenFiltered: Array[ASTNode] = children.filter(isCorrectBlock)
        val childHead :: childTail= childrenFiltered.toList
        subBlocks.add(getSubBlock(block, childHead))
        val indent: Indent = {
          val prevNonWsNode: Option[PsiElement] = psi.prevSibling match {
            case Some(prev@Whitespace(s)) =>
              if (s.contains("\n")) None
              else prev.prevSibling
            case prev =>
              prev
          }
          prevNonWsNode.map(_.elementType) match {
            case Some(ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tLPARENTHESIS) if scalaSettings.KEEP_COMMENTS_ON_SAME_LINE =>
              Indent.getNormalIndent
            case _ =>
              Indent.getNoneIndent
          }
        }
        subBlocks.add(getSubBlock(block, childTail.head, childTail.last, null, Some(indent)))
        return subBlocks
      case _ =>
    }

    val alignment: Alignment =
      if (mustAlignment(node)) Alignment.createAlignment
      else null
    var alternateAlignment: Alignment = null
    for (child <- children if isCorrectBlock(child)) {
      val childAlignment: Alignment = {
        node.getPsi match {
          case params: ScParameters =>
            val firstParameterStartsFromNewLine =
              params.clauses.headOption.flatMap(_.parameters.headOption).exists(_.startsFromNewLine()) ||
                commonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
            if (firstParameterStartsFromNewLine && !scalaSettings.INDENT_FIRST_PARAMETER) null
            else alignment
          case _: ScParameterClause =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => null
              case _ => alignment
            }
          case _: ScArgumentExprList =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS =>
                alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS =>
                if (cs.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              case ScCodeBlockElementType.BlockExpression if ss.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
              case _ if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          case patt: ScPatternArgumentList =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if patt.missedLastExpr && cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS =>
                alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS =>
                if (cs.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              case ScCodeBlockElementType.BlockExpression if ss.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
              case _ if cs.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          case _: ScMethodCall | _: ScReferenceExpression =>
            if (child.getElementType == ScalaTokenTypes.tIDENTIFIER &&
              child.getPsi.getParent.isInstanceOf[ScReferenceExpression] &&
              child.getPsi.getParent.asInstanceOf[ScReferenceExpression].qualifier.isEmpty) null
            else if (child.getPsi.isInstanceOf[ScExpression]) null
            else alignment
          case _: ScXmlStartTag | _: ScXmlEmptyTag =>
            child.getElementType match {
              case ScalaElementType.XML_ATTRIBUTE => alignment
              case _ => null
            }
          case _: ScXmlElement =>
            child.getElementType match {
              case ScalaElementType.XML_START_TAG | ScalaElementType.XML_END_TAG => alignment
              case _ => null
            }
          case _: ScParameter =>
            child.getElementType match {
              case ScalaTokenTypes.tCOLON if ss.ALIGN_TYPES_IN_MULTILINE_DECLARATIONS =>
                child.getPsi.nullSafe.map(_.getParent).map(_.getParent).map { rootPsi =>
                  val map = multiLevelAlignmentMap(rootPsi.getProject)
                  map.get(ScalaTokenTypes.tCOLON).flatMap(_.find(_.shouldAlign(child))) match {
                    case Some(multiAlignment) => multiAlignment.getAlignment
                    case None =>
                      val multiAlignment = ElementPointerAlignmentStrategy.typeMultiLevelAlignment(rootPsi)
                      assert(multiAlignment.shouldAlign(child))
                      map.update(ScalaTokenTypes.tCOLON, multiAlignment :: map.getOrElse(ScalaTokenTypes.tCOLON, List()))
                      multiAlignment.getAlignment
                  }
                }.getOrElse(alignment)
              case _ => alignment
            }
          case _ => alignment
        }
      }

      val needFlattenInterpolatedStrings = child.getFirstChildNode == null &&
        child.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING &&
        ss.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE
      if (needFlattenInterpolatedStrings) {
        subBlocks.addAll(getMultilineStringBlocks(child, block))
      } else {
        subBlocks.add(getSubBlock(block, child, null, childAlignment))
      }
    }

    subBlocks
  }

  private def getCaseClauseGroupSubBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]

    def getPrevGroupNode(node: ASTNode): ASTNode = {
      val nodePsi = node.getPsi
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
      if (breaks != 1) null
      else if (prev == null) null
      else prev.getNode
    }

    var prevChild: ASTNode = null
    for (child <- children if isCorrectBlock(child)) {
      val childAlignment = getChildAlignment(node, child, getPrevGroupNode, Seq(ScalaTokenTypes.FUNTYPE_ANY_TOKEN_SET))
      subBlocks.add(getSubBlock(block, child, null, childAlignment))
      prevChild = child
    }

    subBlocks
  }

  private def getFieldGroupSubBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]

    def getPrevGroupNode(node: ASTNode): ASTNode = {
      val nodePsi = node.getPsi
      var prev = nodePsi.getPrevSibling
      var breaks = 0
      def isOk(psi: PsiElement): Boolean = psi match {
        case ElementType(t) if t == ScalaTokenTypes.tSEMICOLON =>
          false
        case _: ScVariableDeclaration | _: ScValueDeclaration
          if nodePsi.isInstanceOf[ScPatternDefinition] || nodePsi.isInstanceOf[ScVariableDefinition] =>
          breaks += 2
          false
        case _: ScVariableDefinition | _: ScPatternDefinition
          if nodePsi.isInstanceOf[ScValueDeclaration] || nodePsi.isInstanceOf[ScValueDeclaration] =>
          breaks += 2
          false
        case _: ScVariable | _: ScValue =>
          def hasModifierList(psi: PsiElement) = psi match {
            case mod: ScModifierListOwner if mod.getModifierList.getTextLength == 0 => false
            case _ => true
          }
          if (hasModifierList(psi) != hasModifierList(nodePsi)) {
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
      if (breaks != 1) null
      else if (prev == null) null
      else prev.getNode
    }

    var prevChild: ASTNode = null
    for (child <- children if isCorrectBlock(child)) {
      //TODO process rare case of first-line comment before one of the fields  for SCL-10000 here
      val childAlignment = getChildAlignment(node, child, getPrevGroupNode, FieldGroupSubBlocksTokenSets)
      subBlocks.add(getSubBlock(block, child, null, childAlignment))
      prevChild = child
    }
    subBlocks
  }

  @tailrec
  private def getChildAlignment(node: ASTNode, child: ASTNode,
                                getPrevGroupNode: ASTNode => ASTNode,
                                tokenSets: Seq[TokenSet]): Alignment = {
    def createNewAlignment: Alignment = {
      val alignment = Alignment.createAlignment(true)
      child.getPsi.putUserData(fieldGroupAlignmentKey, alignment)
      alignment
    }

    val prev = getPrevGroupNode(node)
    tokenSets.find(_.contains(child.getElementType)) match {
      case Some(ts) =>
        if (prev == null) {
          createNewAlignment
        } else {
          val prevChild = prev.findChildByType(ts)
          if (prevChild == null) {
            getChildAlignment(prev, child, getPrevGroupNode, tokenSets)
          } else {
            val alignment = node.getPsi.getUserData(fieldGroupAlignmentKey)
            val newAlignment = if (alignment == null) createNewAlignment else alignment
            child.getPsi.putUserData(fieldGroupAlignmentKey, newAlignment)
            newAlignment
          }
        }
      case None =>
        null
    }
  }

  private def getExtendsSubBlocks(node: ASTNode, block: ScalaBlock, extBlock: ScExtendsBlock): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val firstChild = extBlock.getFirstChild
    if (firstChild == null) return subBlocks
    val tempBody = extBlock.templateBody

    val lastChild = tempBody.map(_.getPrevSiblingNotWhitespace).getOrElse(extBlock.getLastChild)
    if (lastChild != null) {
      val alignment =
        if (ss.ALIGN_EXTENDS_WITH == ScalaCodeStyleSettings.ALIGN_TO_EXTENDS) Alignment.createAlignment(false)
        else null
      subBlocks.add(getSubBlock(block, firstChild.getNode, lastChild.getNode, alignment))
    }

    tempBody match {
      case Some(x) =>
        subBlocks.add(getSubBlock(block, x.getNode))
      case _ =>
    }

    subBlocks
  }

  private def getForSubBlocks(node: ASTNode, block: ScalaBlock, children: Array[ASTNode]): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]()

    def addSubBlock(node: ASTNode, lastNode: ASTNode): Unit = {
      val subBlock = getSubBlock(block, node, lastNode)
      subBlocks.add(subBlock)
    }

    var prevChild: ASTNode = null
    def addTail(tail: List[ASTNode]): Unit = {
      for (child <- tail) {
        if (child.getElementType != ScalaTokenTypes.kYIELD) {
          if (prevChild != null && prevChild.getElementType == ScalaTokenTypes.kYIELD) {
            addSubBlock(prevChild, child)
          } else {
            addSubBlock(child, null)
          }
        }
        prevChild = child
      }
      if (prevChild != null && prevChild.getElementType == ScalaTokenTypes.kYIELD) {
        //add a block for 'yield' in case of incomplete for statement (expression after yield is missing)
        addSubBlock(prevChild, null)
      }
    }

    @tailrec
    def addFor(children: List[ASTNode]): Unit = children match {
      case forWord :: tail if forWord.getElementType == ScalaTokenTypes.kFOR =>
        addSubBlock(forWord, null)
        addFor(tail)
      case lParen :: tail if ScalaTokenTypes.LBRACE_LPARENT_TOKEN_SET.contains(lParen.getElementType) =>
        val closingType =
          if (lParen.getElementType == ScalaTokenTypes.tLPARENTHESIS) ScalaTokenTypes.tRPARENTHESIS
          else ScalaTokenTypes.tRBRACE
        val after = tail.dropWhile(_.getElementType != closingType)
        after match {
          case Nil =>
            addTail(children)
          case headAfter :: tailAfter =>
            addSubBlock(lParen, headAfter)
            addTail(tailAfter)
        }
      case _ =>
        addTail(children)
    }
    addFor(children.filter(isCorrectBlock).toList)
    subBlocks
  }

  private def getIfSubBlocks(node: ASTNode, block: ScalaBlock, alignment: Alignment): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val firstChildNode = node.getFirstChildNode
    var child = firstChildNode
    while (child.getTreeNext != null && child.getTreeNext.getElementType != ScalaTokenTypes.kELSE) {
      child = child.getTreeNext
    }

    val firstBlock = getSubBlock(block, firstChildNode, child, alignment)
    subBlocks.add(firstBlock)

    if (child.getTreeNext != null) {
      val firstChild = child.getTreeNext
      child = firstChild
      while (child.getTreeNext != null) {
        child.getTreeNext.getPsi match {
          case _: ScIf if cs.SPECIAL_ELSE_IF_TREATMENT =>
            subBlocks.add(getSubBlock(block, firstChild, child, alignment, Some(firstBlock.indent)))
            subBlocks.addAll(getIfSubBlocks(child.getTreeNext, block, alignment))
          case _ =>
        }
        child = child.getTreeNext
      }
      if (subBlocks.size == 1) {
        subBlocks.add(getSubBlock(block, firstChild, child, alignment, Some(firstBlock.indent)))
      }
    }

    subBlocks
  }

  private def getMultilineStringBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    def interpolatedRefLength(node: ASTNode): Int = {
      if (node.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING) {
        node.getPsi.getParent match {
          case str: ScInterpolatedStringLiteral => str.reference.map(_.refName.length).getOrElse(0)
          case _ => 0
        }
      } else 0
    }
    val subBlocks = new util.ArrayList[Block]

    val alignment = null
    val interpolatedOpt = Option(PsiTreeUtil.getParentOfType(node.getPsi, classOf[ScInterpolatedStringLiteral]))
    val validAlignment = interpolatedOpt
      .flatMap(cachedAlignment)
      .getOrElse(Alignment.createAlignment(true))
    val wrap: Wrap = Wrap.createWrap(WrapType.NONE, true)
    val marginChar = "" + MultilineStringUtil.getMarginChar(node.getPsi)
    val marginIndent = ss.MULTI_LINE_STRING_MARGIN_INDENT

    val indent = Indent.getNoneIndent
    val simpleIndent = Indent.getAbsoluteNoneIndent
    val prefixIndent = Indent.getSpaceIndent(marginIndent + interpolatedRefLength(node), true)

    def relativeRange(start: Int, end: Int, shift: Int = 0): TextRange =
      new TextRange(node.getStartOffset + start + shift, node.getStartOffset + end + shift)

    val lines = node.getText.split("\n")
    var acc = 0
    lines foreach { line =>
      val trimmedLine = line.trim()
      val lineLength = line.length
      val linePrefixLength = if (settings.useTabCharacter(ScalaFileType.INSTANCE)) {
        val tabsCount = line.prefixLength(_ == '\t')
        tabsCount /* *settings.getTabSize(ScalaFileType.INSTANCE)*/ + line.substring(tabsCount).prefixLength(_ == ' ')
      } else {
        line.prefixLength(_ == ' ')
      }

      if (trimmedLine.startsWith(marginChar)) {
        val range = relativeRange(linePrefixLength, linePrefixLength + 1, acc)
        subBlocks.add(new StringLineScalaBlock(range, node, block, validAlignment, prefixIndent, null, settings))

        if (lineLength > linePrefixLength + 2 && line.charAt(linePrefixLength + 1) == ' ' ||
          lineLength > linePrefixLength + 1 && line.charAt(linePrefixLength + 1) != ' ') {
          val suffixOffset = if (line.charAt(linePrefixLength + 1) == ' ') 2 else 1
          val range = relativeRange(linePrefixLength + suffixOffset, lineLength, acc)
          subBlocks.add(new StringLineScalaBlock(range, node, block, null, indent, wrap, settings))
        }
      } else if (trimmedLine.length > 0) {
        val (range, myIndent, myAlignment) =
          if (trimmedLine.startsWith("\"\"\"")) {
            if (acc != 0) {
              (relativeRange(linePrefixLength, lineLength, acc), Indent.getSpaceIndent(0, true), alignment)
            } else if (trimmedLine.startsWith("\"\"\"|") && lineLength > 3) {
              val range = relativeRange(0, 3)
              subBlocks.add(new StringLineScalaBlock(range, node, block, alignment, Indent.getNoneIndent, null, settings))
              //now, return block parameters for text after the opening quotes
              (relativeRange(3, lineLength), Indent.getNoneIndent, validAlignment)
            } else {
              (relativeRange(0, lineLength), Indent.getNoneIndent, alignment)
            }
          } else {
            (relativeRange(0, lineLength, acc), simpleIndent, alignment)
          }
        subBlocks.add(new StringLineScalaBlock(range, node, block, myAlignment, myIndent, null, settings))
      }

      acc += lineLength + 1
    }

    subBlocks
  }

  private def getInfixBlocks(node: ASTNode, block: ScalaBlock, parentAlignment: Alignment = null): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]
    val children = node.getChildren(null)
    val alignment =
      if (parentAlignment != null) parentAlignment
      else if (mustAlignment(node)) Alignment.createAlignment
      else null
    for (child <- children) {
      if (InfixElementsTokenSet.contains(child.getElementType) && infixPriority(node) == infixPriority(child)) {
        subBlocks.addAll(getInfixBlocks(child, block, alignment))
      } else if (isCorrectBlock(child)) {
        subBlocks.add(getSubBlock(block, child, null, alignment))
      }
    }
    subBlocks
  }

  private def infixPriority(node: ASTNode): Int = node.getPsi match {
    case inf: ScInfixExpr => ParserUtils.priority(inf.operation.getText, assignments = true)
    case inf: ScInfixPattern => ParserUtils.priority(inf.operation.getText, assignments = false)
    case inf: ScInfixTypeElement => ParserUtils.priority(inf.operation.getText, assignments = false)
    case _ => 0
  }

  private def getMethodCallOrRefExprSubBlocks(node: ASTNode, block: ScalaBlock,
                                              delegatedChildren: List[ASTNode] = List()): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    def addSubBlock(node: ASTNode, lastNode: ASTNode, alignment: Alignment,
                    context: Option[SubBlocksContext] = None, wrap: Option[Wrap] = None): Unit = {
      val subBlock = getSubBlock(block, node, lastNode, alignment, wrap = wrap, context = context)
      subBlocks.add(subBlock)
    }

    val parentBlock = block.parentBlock
    val insideChainedCall = parentBlock.nullSafe.map(_.getNode.getElementType).exists(MethodCallOrReferenceTokenSet.contains)
    val nextDotInCallChain: Option[ScalaBlock] =
      if (insideChainedCall) {
        parentBlock.getSubBlocks.asScala.toList match {
          case _ :: (dot: ScalaBlock) :: Nil if dot.getNode.getElementType == ScalaTokenTypes.tDOT =>
            Some(dot)
          case _ => None
        }
      } else None

    val parentDotAlignment: Option[Alignment] = nextDotInCallChain.map(_.alignment)
    val parentDotWrap: Option[Wrap] = nextDotInCallChain.map(_.wrap)

    val alignment: Alignment = if (mustAlignment(node)) Alignment.createAlignment else null
    val dotAlignment: Alignment = parentDotAlignment.getOrElse(alignment)
    val dotWrap: Wrap = parentDotWrap.getOrElse(block.suggestedWrap)

    val children = node.getChildren(null).filter(isCorrectBlock).toList
    children match {
      //don't check for element types other then absolutely required - they do not matter
      case caller :: args :: Nil if args.getPsi.isInstanceOf[ScArgumentExprList] =>
        val blocks = getMethodCallOrRefExprSubBlocks(caller, block, args :: delegatedChildren)
        subBlocks.addAll(blocks)

      case expr :: dot :: id :: Nil if dot.getElementType == ScalaTokenTypes.tDOT =>
        addSubBlock(expr, null, alignment = null)
        val context = SubBlocksContext(id, dotAlignment, delegatedChildren)
        addSubBlock(dot, lastNode(id :: delegatedChildren), dotAlignment, Some(context), Some(dotWrap))

      case expr :: typeArgs :: Nil if typeArgs.getPsi.isInstanceOf[ScTypeArgs] =>
        val actualAlignment =
          if (nextDotInCallChain.exists(_.getNode.getPsi.followedByNewLine())) dotAlignment
          else alignment
        val context = SubBlocksContext(typeArgs, actualAlignment, delegatedChildren)
        addSubBlock(expr, lastNode(typeArgs :: delegatedChildren), actualAlignment, Some(context))

      case expr :: Nil =>
        val actualAlignment =
          if (nextDotInCallChain.exists(_.getNode.getPsi.followedByNewLine())) dotAlignment
          else alignment
        val context = SubBlocksContext(expr, alignment, delegatedChildren)
        addSubBlock(expr, lastNode(delegatedChildren), actualAlignment, context = Some(context))

      case _ =>
        for (child <- (children ++ delegatedChildren).filter(isCorrectBlock)) {
          addSubBlock(child, null, dotAlignment)
        }
    }

    subBlocks
  }

  @inline
  private def lastNode(nodes: Seq[ASTNode]): ASTNode = nodes.sortBy(_.getTextRange.getStartOffset).lastOption.orNull

  private def mustAlignment(node: ASTNode): Boolean = {
    import commonSettings._
    node.getPsi match {
      case _: ScXmlStartTag => true //todo:
      case _: ScXmlEmptyTag => true //todo:
      case _: ScParameters if ALIGN_MULTILINE_PARAMETERS => true
      case _: ScParameterClause if ALIGN_MULTILINE_PARAMETERS => true
      case _: ScArgumentExprList if ALIGN_MULTILINE_PARAMETERS_IN_CALLS || ALIGN_MULTILINE_METHOD_BRACKETS => true
      case _: ScPatternArgumentList if ALIGN_MULTILINE_PARAMETERS_IN_CALLS || ALIGN_MULTILINE_METHOD_BRACKETS => true
      case _: ScEnumerators if ALIGN_MULTILINE_FOR => true
      case _: ScParenthesisedExpr if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedTypeElement if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedPattern if ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScInfixExpr if ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixPattern if ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixTypeElement if ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScCompositePattern if ss.ALIGN_COMPOSITE_PATTERN => true
      case _: ScMethodCall | _: ScReferenceExpression | _: ScThisReference | _: ScSuperReference if ALIGN_MULTILINE_CHAINED_METHODS => true
      case _ => false
    }
  }

  private def applyInner(node: ASTNode, lastNode: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    def childBlock(child: ASTNode, context: Option[SubBlocksContext] = None): ScalaBlock = {
      val lastNode = block.getChildBlockLastNode(child)
      val alignment = block.getCustomAlignment(child).orNull
      getSubBlock(block, child, lastNode, alignment, context = context)
    }

    if (insideScalaDocComment(node)) {
      applyInnerScaladoc(node, lastNode, block, subBlocks)
    } else {
      var child: ASTNode = node
      do {
        if (isCorrectBlock(child)) {
          if (child.getPsi.isInstanceOf[ScTemplateParents]) {
            subBlocks.addAll(getTemplateParentsBlocks(child, block))
          } else {
            val context = block.subBlocksContext.flatMap(_.childrenAdditionalContexts.get(child))
            subBlocks.add(childBlock(child, context))
          }
        }
      } while (child != lastNode && {
        child = child.getTreeNext
        child != null
      })
    }

    //it is not used right now, but could come in handy later
    for {
      context <- block.subBlocksContext
      additionalNode <- context.additionalNodes
    } {
      subBlocks.add(childBlock(additionalNode))
    }

    subBlocks
  }

  private def insideScalaDocComment(node: ASTNode): Boolean = {
    val insideIncompleteScalaDocTag =
      node.getTreeParent.nullSafe.exists(_.getElementType == ScalaDocElementTypes.DOC_TAG) &&
        node.getPsi.isInstanceOf[PsiErrorElement]
    ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(node.getElementType) || insideIncompleteScalaDocTag
  }

  private def applyInnerScaladoc(node: ASTNode, lastNode: ASTNode, block: ScalaBlock, subBlocks: util.List[Block]): Unit = {
    val children = ArrayBuffer[ASTNode]()
    var scaladocNode = node.getElementType match {
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN =>
        subBlocks.add(getSubBlock(block, node, indent = Some(Indent.getNoneIndent)))
        node.getTreeNext
      case _ =>
        node
    }

    do {
      if (scaladocNode.getText.contains("\n")) {
        flattenChildren(scaladocNode, children)
      } else {
        children += scaladocNode
      }
    } while (scaladocNode != lastNode && (scaladocNode = scaladocNode.getTreeNext, true)._2)

    val normalAlignment =
      block.parentBlock.subBlocksContext.flatMap(_.alignment)
        .getOrElse(Alignment.createAlignment(true))

    children.view.filter(isCorrectBlock).foreach { child =>
      val firstSibling = node.getTreeParent.getFirstChildNode

      val isDataInsideDocTag =
        node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
          child.getElementType != ScalaDocTokenType.DOC_WHITESPACE &&
          child.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
          child != firstSibling &&
          firstSibling.getElementType == ScalaDocTokenType.DOC_TAG_NAME &&
          child.getText.trim.length > 0

      val (childAlignment, childWrap) =
        if (isDataInsideDocTag) {
          val docTagName = firstSibling.getText
          val alignment = docTagName match {
            case "@param" | "@tparam" => if (ss.SD_ALIGN_PARAMETERS_COMMENTS) normalAlignment else null
            case "@return" => if (ss.SD_ALIGN_RETURN_COMMENTS) normalAlignment else null
            case "@throws" => if (ss.SD_ALIGN_EXCEPTION_COMMENTS) normalAlignment else null
            case _ if child.getElementType == ScalaDocTokenType.DOC_INNER_CODE => null
            case _ if child.getElementType == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG => null
            case _ => if (ss.SD_ALIGN_OTHER_TAGS_COMMENTS) normalAlignment else null
          }
          val noWrap = Wrap.createWrap(WrapType.NONE, false)
          (alignment, noWrap)
        } else {
          (null, arrangeSuggestedWrapForChild(block, child, block.suggestedWrap))
        }
      subBlocks.add(getSubBlock(block, child, null, childAlignment, wrap = Some(childWrap)))
    }
  }

  private def flattenChildren(multilineNode: ASTNode, buffer: ArrayBuffer[ASTNode]): Unit = {
    for (nodeChild <- multilineNode.getChildren(null)) {
      if (nodeChild.textContains('\n') && nodeChild.getFirstChildNode != null) {
        flattenChildren(nodeChild, buffer)
      } else {
        buffer += nodeChild
      }
    }
  }

  private def getTemplateParentsBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    import ScalaCodeStyleSettings._
    val alignSetting = ss.ALIGN_EXTENDS_WITH
    val alignment = if (alignSetting == ALIGN_TO_EXTENDS) block.getAlignment else Alignment.createAlignment(true)

    val children = node.getChildren(null)
    for (child <- children if isCorrectBlock(child)) {
      val actualAlignment = (child.getElementType, alignSetting) match {
        case (_, DO_NOT_ALIGN) => null
        case (ScalaTokenTypes.kWITH | ScalaTokenTypes.kEXTENDS, ON_FIRST_ANCESTOR) => null
        case _ => alignment
      }
      val lastNode = block.getChildBlockLastNode(child)
      val context = block.subBlocksContext.flatMap(_.childrenAdditionalContexts.get(child))
      subBlocks.add(getSubBlock(block, child, lastNode, actualAlignment, context = context))
    }
    subBlocks
  }

  private def getSubBlock(parent: ScalaBlock,
                          node: ASTNode,
                          lastNode: ASTNode = null,
                          alignment: Alignment = null,
                          indent: Option[Indent] = None,
                          wrap: Option[Wrap] = None,
                          context: Option[SubBlocksContext] = None): ScalaBlock = {
    val indentFinal = indent.getOrElse(ScalaIndentProcessor.getChildIndent(parent, node))
    val wrapFinal = wrap.getOrElse(arrangeSuggestedWrapForChild(parent, node, parent.suggestedWrap))
    new ScalaBlock(parent, node, lastNode, alignment, indentFinal, wrapFinal, settings, context)
  }
}