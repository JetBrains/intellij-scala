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
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder.{FoldingInfo, MatchExprOrMatchType}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScParenthesisedTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScOptionalBracesOwner}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings

import scala.collection._
import scala.jdk.CollectionConverters._

// TODO: do not use ASTNode.getText or PsiElement.getText
// TODO: extract shared string literals, like "{...}"
//noinspection InstanceOf
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
    if (isMultiline(node) || isMultilineImport(node) || isNonEmptyExtensionBodyOnNewLine(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT |  ScalaDocElementTypes.SCALA_DOC_COMMENT =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case EXTENSION_BODY =>
          // extensions template body do not support `:` in the beginning,
          // we should capture new line before
          val isExtensionsTemplateBody = node.getTreeParent.getElementType == ScalaElementType.EXTENSION
          val range = if (isExtensionsTemplateBody) captureWhitespaceBefore(node, nodeTextRange) else nodeTextRange
          descriptors.add(new FoldingDescriptor(node, range))
        case TEMPLATE_BODY => descriptors.add(new FoldingDescriptor(node, nodeTextRange))
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
        case p: ScPackaging  =>
          p.findExplicitMarker match {
            case Some(marker) => // `{` or `:`
              val start = nodeTextRange.getStartOffset + marker.getStartOffsetInParent
              val end = nodeTextRange.getEndOffset
              val range = new TextRange(start, end)
              descriptors.add(new FoldingDescriptor(node, range))
            case _ =>
          }
        case p: ScStringLiteral if p.isMultiLineString =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case args: ScArgumentExprList if args.isArgsInParens =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case definition: ScDefinitionWithAssignment =>
          val bodyOpt = definitionBody(definition)
          bodyOpt.foreach { body =>
            val start =
              if (body.startsFromNewLine()) definition.assignment.map(_.endOffset).getOrElse(body.startOffset)
              else body.startOffset
            val end = definition.endOffset // will automatically include end marker
            val rangeNew = TextRange.create(start, end)
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
              case _: ScArgumentExprList | _: ScFor | _: ScWhile | _: ScDo | _: ScIf =>
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
      if (!CustomFoldingBuilder.isCustomRegionElement(node.getPsi)) {
        addCommentFolds(node.getPsi.asInstanceOf[PsiComment], processedComments, descriptors)
      }
    } else if (node.getElementType == SIMPLE_TYPE && node.getText == "Unit" &&
      node.getPsi.getParent.is[ScFunctionDefinition] &&
      ScalaCodeStyleSettings.getInstance(node.getPsi.getProject).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT && foldingSettings.isCollapseCustomRegions) {

      node.getPsi match {
        case sc: ScalaPsiElement =>
          (sc.getPrevSiblingNotWhitespace, sc.getNextSiblingNotWhitespace) match {
            case (a1: PsiElement, a2: PsiElement)
              if a1.getNode.getElementType == ScalaTokenTypes.tCOLON && a2.getNode.getElementType == ScalaTokenTypes.tASSIGN =>
              val startElement =
                if (a1.getPrevSibling.is[PsiWhiteSpace]) a1.getPrevSibling
                else a1
              val endElement =
                if (a2.getNextSibling.is[PsiWhiteSpace]) a2.getNextSibling
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

  // include end marker if it goes after the block
  private def elementRangeWithEndMarkerAttached(element: PsiElement, elementRange: TextRange): TextRange =
    element.nextSiblingNotWhitespace match {
      case Some(end: ScEnd) => TextRange.create(elementRange.getStartOffset, end.endOffset)
      case _                => elementRange
    }

  private def captureWhitespaceBefore(node: ASTNode, nodeRange: TextRange): TextRange =
    node.getTreePrev match {
      case ws: PsiWhiteSpace => TextRange.create(ws.getStartOffset, nodeRange.getEndOffset)
      case _                 => nodeRange
    }

  // TODO: maybe extract some proper base method should be extracted to ScDefinitionWithAssignment?
  //  currently there are "expr", "body", none
  private def definitionBody(da: ScDefinitionWithAssignment): Option[PsiElement] = {
    def defaultBodyImpl = da.assignment.flatMap(_.nextSiblingNotWhitespaceComment).filterNot(_.is[PsiErrorElement])

    da match {
      case d: ScPatternDefinition  => d.expr
      case d: ScVariableDefinition => d.expr
      case d: ScFunctionDefinition => d.body
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

  //noinspection ScalaUnusedSymbol
  private def printFoldingsDebugInfo(descriptors: java.util.List[FoldingDescriptor]): Unit = {
    val infos = descriptors.iterator().asScala.map { d =>
      s"  ${d.getRange}  ${d.getPlaceholderText}"
    }.mkString("\n")
    println(s"Foldings:\n$infos")
  }

  private def getPlaceholderText(element: ScOptionalBracesOwner): String =
    element.getEnclosingStartElement.map(_.elementType) match {
      case Some(ScalaTokenTypes.tLBRACE) => "{...}"
      case Some(ScalaTokenTypes.tCOLON)  => ":..."
      case _                             => " ..."
    }

  override def getLanguagePlaceholderText(node: ASTNode, textRange: TextRange): String = {
    if (isMultiline(node) || isMultilineImport(node) || isNonEmptyExtensionBodyOnNewLine(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
        case ScalaDocElementTypes.SCALA_DOC_COMMENT => return "/**...*/"
        case ImportStatement => return "..."
        case MatchExprOrMatchType() =>
          val info = multilineBodyInMatch(node)
          return info.map(_.placeholder).getOrElse("{...}")
        case _ =>
      }

      node.getPsi match {
        case literal: ScStringLiteral if literal.isMultiLineString =>
          return "\"\"\"...\"\"\""
        case _: ScArgumentExprList =>
          return "(...)"
        case _: ScBlockImpl => // it's basically case clause body
          return "..."
        case e: ScOptionalBracesOwner =>
          val result = getPlaceholderText(e)
          return result
        case da: ScDefinitionWithAssignment =>
          val bodyOpt = definitionBody(da)
          val res = bodyOpt.map {
            case block: ScBlockExpr => getPlaceholderText(block)
            case single =>
              if (single.startsFromNewLine()) " ..."
              else "..."
          }.orNull
          return res
        case _ =>
      }
    }

    node.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT =>
        if (!CustomFoldingBuilder.isCustomRegionElement(node.getPsi))
          return "/.../"
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
      val isFirstToplevelElement = parentElementType.is[ScStubFileElementType] && node.getTreePrev == null
      isFirstToplevelElement && ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(node.getElementType)
    }

    if (isFileHeader && foldingSettings.isCollapseFileHeaders)
      true
    else if (parentElementType.is[ScStubFileElementType] &&
      node.getElementType == ImportStatement &&
            foldingSettings.isCollapseImports) true
    else {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT
          if foldingSettings.isCollapseBlockComments => true
        case ScalaTokenTypes.tLINE_COMMENT
          if !CustomFoldingBuilder.isCustomRegionElement(psi) &&
                  foldingSettings.isCollapseLineComments => true
        case ScalaTokenTypes.tLINE_COMMENT
          if CustomFoldingBuilder.isCustomRegionElement(psi) &&
                  foldingSettings.isCollapseCustomRegions => true
        case ScalaDocElementTypes.SCALA_DOC_COMMENT
          if foldingSettings.isCollapseScalaDocComments => true
        case TEMPLATE_BODY
          if foldingSettings.isCollapseTemplateBodies => true
        case PACKAGING
          if foldingSettings.isCollapsePackagings => true
        case ImportStatement
          if foldingSettings.isCollapseImports => true
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

  override def isCustomFoldingRoot(node: ASTNode): Boolean = node.getElementType match {
    /** @see [[ScOptionalBracesOwner]] hierarchy */
    case PACKAGING |
         TEMPLATE_BODY |
         EXTENSION_BODY |
         BLOCK |
         ScCodeBlockElementType.BlockExpression => true
    case _ => false
  }

  private def isMultiline(node: ASTNode): Boolean =
    node.textContains('\n')

  /**
   * @param node represents match expression or match type (Scala 3)
   * @return Some(folding range, folding placegolder) - if the match is multiline andshould be folder<br>
   *          None - otherise*/
  private def multilineBodyInMatch(node: ASTNode): Option[FoldingInfo] = {
    val children: Iterator[PsiElement] = node.getPsi.children

    val mathKeyword = children.dropWhile(_.elementType != ScalaTokenTypes.kMATCH).nextOption()
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

  private def isNonEmptyExtensionBodyOnNewLine(node: ASTNode): Boolean = node.getElementType == EXTENSION_BODY &&
    node.getPsi.startsFromNewLine()

  private def isMultilineImport(node: ASTNode): Boolean = {
    if (node.getElementType != ImportStatement) return false
    var next = node.getTreeNext
    var flag = false
    while (next != null && (next.getPsi.is[LeafPsiElement] || next.getElementType == ImportStatement)) {
      if (next.getElementType == ImportStatement) flag = true
      next = next.getTreeNext
    }
    flag
  }

  private def isGoodImport(node: ASTNode): Boolean = {
    var prev = node.getTreePrev
    while (prev != null && prev.getPsi.is[LeafPsiElement]) prev = prev.getTreePrev
    if (prev == null || prev.getElementType != ImportStatement) true
    else false
  }

  private def getImportEnd(node: ASTNode): Int = {
    var next = node
    var last = next.getTextRange.getEndOffset
    while (next != null && (next.getPsi.is[LeafPsiElement] || next.getElementType == ImportStatement)) {
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
      if (current != null && CustomFoldingBuilder.isCustomRegionElement(current)) {
        flag = false
      }
    }

    if (end != null) {
      descriptors add new FoldingDescriptor(comment,
        new TextRange(comment.getTextRange.getStartOffset, end.getTextRange.getEndOffset))
    }
  }
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
