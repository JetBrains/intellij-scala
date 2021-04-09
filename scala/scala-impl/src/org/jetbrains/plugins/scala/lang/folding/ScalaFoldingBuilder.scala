package org.jetbrains.plugins.scala.lang.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.{CustomFoldingBuilder, FoldingDescriptor}
import com.intellij.openapi.editor.{Document, FoldingGroup}
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{ElementType, IteratorExt, ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder.{FoldingInfo, MatchExprOrMatchType}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScParenthesisedTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings

import scala.collection._
import scala.jdk.CollectionConverters._

// TODO: do not use ASTNode.getText or PsiElement.getText
// TODO: extract shared string literals, like "{...}"
class ScalaFoldingBuilder extends CustomFoldingBuilder with PossiblyDumbAware {
  import ScalaElementType._
  import ScalaFoldingUtil._

  private val foldingSettings = ScalaCodeFoldingSettings.getInstance()

  override def isDumbAware: Boolean = true

  private def appendDescriptors(node: ASTNode,
                                document: Document,
                                descriptors: java.util.List[FoldingDescriptor],
                                processedComments: mutable.HashSet[PsiElement],
                                processedRegions: mutable.HashSet[PsiElement]): Unit = {
    val nodeTextRange = node.getTextRange
    if (nodeTextRange.getStartOffset + 1 >= nodeTextRange.getEndOffset) return

    val psi = node.getPsi
    if (isMultiline(node) || isMultilineImport(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tSH_COMMENT | ScalaDocElementTypes.SCALA_DOC_COMMENT =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case TEMPLATE_BODY =>
          // extensions template body do not support `:` in the beginning,
          // we should capture new line before
          val isExtensionsTemplateBody  = node.getTreeParent.getElementType == ScalaElementType.Extension
          val range = if (isExtensionsTemplateBody) captureWhitespaceBefore(node, nodeTextRange) else nodeTextRange
          descriptors add new FoldingDescriptor(node, range)
        case ImportStatement if isGoodImport(node) =>
          descriptors add new FoldingDescriptor(node,
            new TextRange(nodeTextRange.getStartOffset + IMPORT_KEYWORD.length + 1, getImportEnd(node)))
        case MatchExprOrMatchType() =>
          val infoOpt = multilineBodyInMatch(node)
          infoOpt.foreach { info =>
            descriptors add new FoldingDescriptor(node, info.range)
          }
        case _ =>
      }

      psi match {
        case p: ScPackaging if p.isExplicit =>
          val identifier = p.findFirstChildByType(ScalaElementType.REFERENCE)
          val body = identifier.flatMap(_.nextElementNotWhitespace)
          val startOffset = body match {
            case Some(el) => el.getStartOffsetInParent
            case None     => nodeTextRange.getStartOffset + PACKAGE_KEYWORD.length + 1
          }
          val range = new TextRange(startOffset, nodeTextRange.getEndOffset)
          descriptors add new FoldingDescriptor(node, range)
        case p: ScLiteral if p.isMultiLineString =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case _: ScArgumentExprList =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case definition: ScDefinitionWithAssignment =>
          val body = definitionBody(definition)
          body.foreach { b =>
            val rangeNew = elementRangeWithEndMarkerAttached(b, b.getTextRange)
            // we generally do not expect bodies empty, but adding this `isEmpty` check just in case
            if (!rangeNew.isEmpty) {
              descriptors.add(new FoldingDescriptor(definition, rangeNew))
            }
          }
        case _ =>
      }

      val treeParent: ASTNode = node.getTreeParent
      if (treeParent != null) {
        psi match {
          case block: ScBlockExpr =>
            // definition with assignment block is attached to the definition itself and is already handled
            if (foldingSettings.isFoldingForAllBlocks && !treeParent.getPsi.is[ScDefinitionWithAssignment]) {
              val rangeNew = elementRangeWithEndMarkerAttached(block, nodeTextRange)
              descriptors.add(new FoldingDescriptor(node, rangeNew))
            }
            else treeParent.getPsi match {
              case _: ScArgumentExprList | _: ScFor | _: ScIf =>
                val rangeNew = elementRangeWithEndMarkerAttached(block, nodeTextRange)
                descriptors.add(new FoldingDescriptor(node, rangeNew))
              case inf: ScInfixExpr if inf.right == node.getPsi => // SCL-3464
                descriptors.add(new FoldingDescriptor(node, nodeTextRange))
              case _ =>
            }
          case _: ScBlock =>
            treeParent.getPsi match {
              // NOTE: it's actually the only possible left variant: case clause,
              // it will be merged with the pattern match above after ScBlockImpl is removed
              case _: ScCaseClause =>
                descriptors.add(new FoldingDescriptor(node, nodeTextRange))
              case _ =>
            }
          case _ =>
        }
      }
    } else if (node.getElementType == TYPE_PROJECTION) {
      node.getPsi match {
        case TypeLambda(typeName, typeParamClause, aliasedType) =>
          val group = FoldingGroup.newGroup("typelambda")
          val range1 = new TextRange(nodeTextRange.getStartOffset, typeParamClause.getTextRange.getStartOffset)
          val d1 = new FoldingDescriptor(node, range1, group) {
            override def getPlaceholderText: String = typeName
          }
          val range2 = new TextRange(aliasedType.getTextRange.getEndOffset, nodeTextRange.getEndOffset)
          val d2 = new FoldingDescriptor(aliasedType.getNode, range2, group) {
            override def getPlaceholderText = ""
          }
          descriptors addAll Seq(d1, d2).asJavaCollection
        case _ =>
      }
    } else if (node.getElementType == ScalaTokenTypes.tLINE_COMMENT) {
      val stack = new mutable.Stack[PsiElement]
      if (!isCustomRegionStart(node.getText) && !isCustomRegionEnd(node.getText)) {
        addCommentFolds(node.getPsi.asInstanceOf[PsiComment], processedComments, descriptors)
      } else if (isCustomRegionStart(node.getText)) {
        if (isTagRegionStart(node.getText)) {
          addCustomRegionFolds(node.getPsi, processedRegions, descriptors, isTagRegion = true, stack)
        } else if (isSimpleRegionStart(node.getText)) {
          addCustomRegionFolds(node.getPsi, processedRegions, descriptors, isTagRegion = false, stack)
        }
      }
    } else if (node.getElementType == SIMPLE_TYPE && node.getText == "Unit" &&
      node.getPsi.getParent.isInstanceOf[ScFunctionDefinition] &&
      ScalaCodeStyleSettings.getInstance(node.getPsi.getProject).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT && foldingSettings.isCollapseCustomRegions) {

      node.getPsi match {
        case sc: ScalaPsiElement =>
          (sc.getPrevSiblingNotWhitespace, sc.getNextSiblingNotWhitespace) match {
            case (a1: PsiElement, a2: PsiElement)
              if a1.getNode.getElementType == ScalaTokenTypes.tCOLON && a2.getNode.getElementType == ScalaTokenTypes.tASSIGN =>
              val startElement =
                if (a1.getPrevSibling.isInstanceOf[PsiWhiteSpace]) a1.getPrevSibling
                else a1
              val endElement =
                if (a2.getNextSibling.isInstanceOf[PsiWhiteSpace]) a2.getNextSibling
                else a2
              descriptors add new FoldingDescriptor(node,
                new TextRange(startElement.getTextRange.getStartOffset, endElement.getTextRange.getEndOffset))
              return
            case _ =>
          }
        case _ =>
      }
    }

    for (child <- node.getChildren(null)) {
      appendDescriptors(child, document, descriptors, processedComments, processedRegions)
    }
  }

  private def captureWhitespaceBefore(node: ASTNode, nodeRange: TextRange): TextRange =
    node.getTreePrev match {
      case ws: PsiWhiteSpace => TextRange.create(ws.getStartOffset, nodeRange.getEndOffset)
      case _                 => nodeRange
    }

  // include end marker if it goes after the block
  private def elementRangeWithEndMarkerAttached(element: PsiElement, elementRange: TextRange): TextRange =
    element.nextSiblingNotWhitespace match {
      case Some(end: ScEnd) => TextRange.create(elementRange.getStartOffset, end.endOffset)
      case _                => elementRange
    }

  // TODO: maybe extract some proper base method should be extracted to ScDefinitionWithAssignment?
  //  currently there are "expr", "body", none
  private def definitionBody(da: ScDefinitionWithAssignment): Option[PsiElement] = {
    def defaultBodyImpl = da.assignment.flatMap(_.nextSiblingNotWhitespaceComment).filterNot(_.is[PsiErrorElement])

    da match {
      case d: ScPatternDefinition  => d.expr
      case d: ScVariableDefinition => d.expr
      case d: ScFunctionDefinition => d.body
      case _: ScGivenAlias         => defaultBodyImpl
      case _: ScTypeAlias          => defaultBodyImpl
      case _                       => None
    }
  }

  override def buildLanguageFoldRegions(descriptors: java.util.List[FoldingDescriptor], root: PsiElement, document: Document,
                               quick: Boolean): Unit = {
    val processedComments = new mutable.HashSet[PsiElement]
    val processedRegions = new mutable.HashSet[PsiElement]
    appendDescriptors(root.getNode, document, descriptors, processedComments, processedRegions)
    //printFoldingsDebugInfo(descriptors)
  }

  private def printFoldingsDebugInfo(descriptors: java.util.List[FoldingDescriptor]): Unit = {
    val infos = descriptors.iterator().asScala.map { d =>
      s"  ${d.getRange}  ${d.getPlaceholderText}"
    }.mkString("\n")
    println(s"Foldings:\n$infos")
  }

  override def getLanguagePlaceholderText(node: ASTNode, textRange: TextRange): String = {
    if (isMultiline(node) || isMultilineImport(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
        case ScalaDocElementTypes.SCALA_DOC_COMMENT => return "/**...*/"
        case TEMPLATE_BODY =>
          val result = Option(node.getFirstChildNode).map(_.getElementType) match {
            case Some(ScalaTokenTypes.tLBRACE) => "{...}"
            case Some(ScalaTokenTypes.tCOLON)  => ":..."
            case _                             => " ..." // extensions do not support `:` in template body
          }
          return result
        case ImportStatement => return "..."
        case MatchExprOrMatchType() =>
          val info = multilineBodyInMatch(node)
          return info.map(_.placeholder).getOrElse("{...}")
        case ScalaTokenTypes.tSH_COMMENT if node.getText.charAt(0) == ':' => return "::#!...::!#"
        case ScalaTokenTypes.tSH_COMMENT => return "#!...!#"
        case _ =>
      }

      node.getPsi match {
        case literal: ScLiteral if literal.isMultiLineString =>
          return "\"\"\"...\"\"\""
        case _: ScArgumentExprList =>
          return "(...)"
        case p: ScPackaging =>
          val marker = p.findExplicitMarker
          val isBraceless = marker.exists(_.elementType == ScalaTokenTypes.tCOLON)
          val res = if (isBraceless) ":..." else "{...}"
          return res
        case block: ScBlockExpr =>
          val result = if (block.isEnclosedByBraces) "{...}" else " ..."
          return result
        case _: ScBlock => // it's basically case clause body
          return "..."
        case da: ScDefinitionWithAssignment =>
          val body = definitionBody(da)
          val res = body match {
            case Some(block: ScBlockExpr) => if (block.isEnclosedByBraces) "{...}" else " ..."
            case Some(_)                  => "..."
            case None                     => null
          }
          return res
        case _ =>
      }
    }

    node.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT =>
        if (!isCustomRegionStart(node.getText))
          return "/.../"
        else {
          if (isTagRegionStart(node.getText)) {
            val customText: String = node.getText.replaceFirst(".*desc\\s*=\\s*\"(.*)\".*", "$1").trim
            return if (customText.isEmpty) "..." else customText
          } else if (isSimpleRegionStart(node.getText)) {
            val customText: String = node.getText.replaceFirst("..?\\s*region(.*)", "$1").trim
            return if (customText.isEmpty) "..." else customText
          }
        }
      case SIMPLE_TYPE => return " "
      case _ => return null
    }

    null
  }

  override def isRegionCollapsedByDefault(node: ASTNode): Boolean = {
    val psi = node.getPsi
    psi.getContainingFile match {
      case sc: ScalaFile if sc.isWorksheetFile => return false
      case _ =>
    }

    val parent = node.getTreeParent
    val parentElementType = parent.getElementType

    val isFileHeader = {
      val isFirstToplevelElement = parentElementType.isInstanceOf[ScStubFileElementType] && node.getTreePrev == null
      isFirstToplevelElement && ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(node.getElementType)
    }

    if (isFileHeader && foldingSettings.isCollapseFileHeaders)
      true
    else if (parentElementType.isInstanceOf[ScStubFileElementType] &&
      node.getElementType == ImportStatement &&
            foldingSettings.isCollapseImports) true
    else {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT
          if foldingSettings.isCollapseBlockComments => true
        case ScalaTokenTypes.tLINE_COMMENT
          if !isCustomRegionStart(node.getText) &&
                  foldingSettings.isCollapseLineComments => true
        case ScalaTokenTypes.tLINE_COMMENT
          if isCustomRegionStart(node.getText) &&
                  foldingSettings.isCollapseCustomRegions => true
        case ScalaDocElementTypes.SCALA_DOC_COMMENT
          if foldingSettings.isCollapseScalaDocComments => true
        case TEMPLATE_BODY
          if foldingSettings.isCollapseTemplateBodies => true
        case PACKAGING
          if foldingSettings.isCollapsePackagings => true
        case ImportStatement
          if foldingSettings.isCollapseImports => true
        case ScalaTokenTypes.tSH_COMMENT
          if foldingSettings.isCollapseShellComments => true
        case MatchExprOrMatchType()
          if foldingSettings.isCollapseMultilineBlocks => true
        case ScCodeBlockElementType.BlockExpression
          if foldingSettings.isCollapseMultilineBlocks => true
        case SIMPLE_TYPE => true
        case _ => psi match {
          case _: ScBlockExpr => parent.getPsi match {
            case _: ScArgumentExprList => foldingSettings.isCollapseMethodCallBodies
            case _                     => false
          }
          case _: ScDefinitionWithAssignment => foldingSettings.isCollapseDefinitionWithAssignmentBodies
          case _: ScTypeProjection           => foldingSettings.isCollapseTypeLambdas
          case _: ScTypeElement              => foldingSettings.isCollapseTypeLambdas
          case l: ScStringLiteral            => foldingSettings.isCollapseMultilineStrings && l.isMultiLineString
          case _: ScArgumentExprList         => foldingSettings.isCollapseMultilineBlocks
          case _                             => false
        }
      }
    }
  }

  private def isMultiline(node: ASTNode): Boolean =
    node.textContains('\n')

  /**
   * @param node represents match expression or match type (Scala 3)
   * @return Some(folding range, folding placegolder) - if the match is multiline andshould be folder<br>
   *          None - otherise*/
  private def multilineBodyInMatch(node: ASTNode): Option[FoldingInfo] = {
    val children: Iterator[PsiElement] = node.getPsi.children

    val mathKeyword = children.dropWhile(_.elementType != ScalaTokenTypes.kMATCH).headOption
    mathKeyword.flatMap  { mk =>
      val textAfter = node.getText.substring(mk.getStartOffsetInParent)
      val isMultiline = textAfter.contains('\n')
      if (isMultiline) {
        val nextLeaf = PsiTreeUtil.nextCodeLeaf(mk)
        val (startOffset, placeholder) = nextLeaf match {
          case openBrace@ElementType(ScalaTokenTypes.tLBRACE) =>
            (openBrace.startOffset, "{...}")
          case _ => // braceless match
            (mk.endOffset, " ...")
        }
        val endOffset = node.getTextRange.getEndOffset
        Some(FoldingInfo(TextRange.create(startOffset, endOffset), placeholder))
      }
      else None
    }
  }

  private def isMultilineImport(node: ASTNode): Boolean = {
    if (node.getElementType != ImportStatement) return false
    var next = node.getTreeNext
    var flag = false
    while (next != null && (next.getPsi.isInstanceOf[LeafPsiElement] || next.getElementType == ImportStatement)) {
      if (next.getElementType == ImportStatement) flag = true
      next = next.getTreeNext
    }
    flag
  }

  private def isGoodImport(node: ASTNode): Boolean = {
    var prev = node.getTreePrev
    while (prev != null && prev.getPsi.isInstanceOf[LeafPsiElement]) prev = prev.getTreePrev
    if (prev == null || prev.getElementType != ImportStatement) true
    else false
  }

  private def getImportEnd(node: ASTNode): Int = {
    var next = node
    var last = next.getTextRange.getEndOffset
    while (next != null && (next.getPsi.isInstanceOf[LeafPsiElement] || next.getElementType == ImportStatement)) {
      if (next.getElementType == ImportStatement || next.getElementType == ScalaTokenTypes.tSEMICOLON) last = next.getTextRange.getEndOffset
      next = next.getTreeNext
    }
    last
  }

  private def addCommentFolds(comment: PsiComment, processedComments: mutable.Set[PsiElement],
                              descriptors: java.util.List[FoldingDescriptor]): Unit = {
    if (processedComments.contains(comment) || comment.getTokenType != ScalaTokenTypes.tLINE_COMMENT) {
      return
    }

    var end: PsiElement = null
    var current: PsiElement = comment.getNextSibling
    var flag = true

    while (current != null && flag) {
      val node: ASTNode = current.getNode
      if (node != null) {
        val elementType: IElementType = node.getElementType
        if (elementType == ScalaTokenTypes.tLINE_COMMENT ) {
          end = current
          processedComments.add(current)
        }
        if (elementType != ScalaTokenTypes.tLINE_COMMENT && elementType != TokenType.WHITE_SPACE) {
          flag = false
        }
      }
      current = current.getNextSibling
      if (current != null && (isCustomRegionStart(current.getText) || isCustomRegionEnd(current.getText))) {
        flag = false
      }
    }

    if (end != null) {
      descriptors add new FoldingDescriptor(comment,
        new TextRange(comment.getTextRange.getStartOffset, end.getTextRange.getEndOffset))
    }
  }

  private def addCustomRegionFolds(element: PsiElement, processedRegions: mutable.Set[PsiElement],
                                   descriptors: java.util.List[FoldingDescriptor], isTagRegion: Boolean,
                                   stack: mutable.Stack[PsiElement]): Unit = {
    var end: PsiElement = null
    var current: PsiElement = element.getNextSibling
    var flag = true

    while (current != null && flag) {
      val node: ASTNode = current.getNode
      if (node != null) {
        val elementType: IElementType = node.getElementType
        if (elementType == ScalaTokenTypes.tLINE_COMMENT && isCustomRegionEnd(node.getText)) {
          if ((isTagRegion && isTagRegionEnd(node.getText)) || (!isTagRegion && isSimpleRegionEnd(node.getText))) {
            if (!processedRegions.contains(current) && stack.isEmpty) {
              end = current
              processedRegions.add(current)
              flag = false
            }
          }
          if (stack.nonEmpty) stack.pop()
        }
        if (elementType == ScalaTokenTypes.tLINE_COMMENT && isCustomRegionStart(node.getText)) {
            stack.push(node.getPsi)
        }
      }
      current = current.getNextSibling
    }

    if (end != null) {
      descriptors add new FoldingDescriptor(element,
        new TextRange(element.getTextRange.getStartOffset, end.getTextRange.getEndOffset))
    }
  }

  private def isCustomRegionStart(elementText: String): Boolean = {
    isTagRegionStart(elementText) || isSimpleRegionStart(elementText)
  }

  private def isTagRegionStart(elementText: String): Boolean = {
    elementText.contains("<editor-fold")
  }

  private def isSimpleRegionStart(elementText: String): Boolean = {
    elementText.contains("region") && elementText.matches("..?\\s*region.*")
  }

  private def isCustomRegionEnd(elementText: String): Boolean = {
    isTagRegionEnd(elementText) || isSimpleRegionEnd(elementText)
  }

  private def isTagRegionEnd(elementText: String): Boolean =
    elementText.contains("</editor-fold")

  private def isSimpleRegionEnd(elementText: String): Boolean =
    elementText.contains("endregion")
}

object ScalaFoldingBuilder {
  private case class FoldingInfo(range: TextRange, placeholder: String)

  private object MatchExprOrMatchType {
    def unapply(el: IElementType): Boolean =
      el == ScalaElementType.MATCH_STMT ||
        el == ScalaElementType.MATCH_TYPE
  }
}

private[folding] object ScalaFoldingUtil {
  val IMPORT_KEYWORD = "import"
  val PACKAGE_KEYWORD = "package"
  val MATCH_KEYWORD = "match"
}

/**
 * Extractor for:
 *
 * ({type λ[α] = Either.LeftProjection[α, Int]})#λ
 *
 * Which can be folded to:
 *
 * λ[α] = Either.LeftProjection[α, Int]
 */
object TypeLambda {
  def unapply(psi: PsiElement): Option[(String, ScTypeParamClause, ScTypeElement)] = psi match {
    case tp: ScTypeProjection =>
      val element = tp.typeElement
      val nameId = tp.nameId
      element match {
        case pte: ScParenthesisedTypeElement =>
          pte.innerElement match {
            case Some(cte: ScCompoundTypeElement) if cte.components.isEmpty =>
              cte.refinement match {
                case Some(ref) =>
                  (ref.holders, ref.types) match {
                    case (scala.Seq(), scala.Seq(tad: ScTypeAliasDefinitionImpl)) if nameId.textMatches(tad.name) =>
                      (tad.typeParametersClause, tad.aliasedTypeElement) match {
                        case (Some(tpc), Some(ate)) =>
                          return Some((nameId.getText, tpc, ate))
                        case _ =>
                      }
                    case _ =>
                  }
                case None =>
              }
            case _ =>
          }
        case _ =>
      }
      None
  }
}
