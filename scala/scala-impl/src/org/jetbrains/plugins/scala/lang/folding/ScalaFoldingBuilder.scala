package org.jetbrains.plugins.scala.lang.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.{CustomFoldingBuilder, FoldingDescriptor}
import com.intellij.openapi.editor.{Document, FoldingGroup}
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScParenthesisedTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings
import org.jetbrains.plugins.scala.worksheet.WorksheetFoldingBuilder

import scala.collection.JavaConverters._
import scala.collection._

class ScalaFoldingBuilder extends CustomFoldingBuilder with PossiblyDumbAware {

  import ScalaElementType._
  import ScalaFoldingUtil._

  private val foldingSettings = ScalaCodeFoldingSettings.getInstance()

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
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tSH_COMMENT | TEMPLATE_BODY |
             ScalaDocElementTypes.SCALA_DOC_COMMENT =>
          if (!isWorksheetResults(node))
            descriptors add new FoldingDescriptor(node, nodeTextRange)
        case ImportStatement if isGoodImport(node) =>
          descriptors add new FoldingDescriptor(node,
            new TextRange(nodeTextRange.getStartOffset + IMPORT_KEYWORD.length + 1, getImportEnd(node)))
        case MATCH_STMT if isMultilineBodyInMatchStmt(node) =>
          descriptors add new FoldingDescriptor(node,
            new TextRange(nodeTextRange.getStartOffset + startOffsetForMatchStmt(node),
              nodeTextRange.getEndOffset))
        case FUNCTION_DEFINITION =>
          psi match {
            case f: ScFunctionDefinition =>
              val (isMultilineBody, textRange, _) = isMultilineFuncBody(f)
              if (isMultilineBody)
                descriptors add new FoldingDescriptor(node, textRange)
            case _ =>
          }
        case _ =>
      }
      psi match {
        case p: ScPackaging if p.isExplicit =>
          descriptors add new FoldingDescriptor(node,
            new TextRange(nodeTextRange.getStartOffset + PACKAGE_KEYWORD.length + 1, nodeTextRange.getEndOffset))
        case p: ScLiteral if p.isMultiLineString =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case _: ScArgumentExprList =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case _: ScBlockExpr
          if foldingSettings.isFoldingForAllBlocks =>
          descriptors add new FoldingDescriptor(node, nodeTextRange)
        case _ =>
      }
      val treeParent: ASTNode = node.getTreeParent
      if (!foldingSettings.isFoldingForAllBlocks &&
        treeParent != null && (treeParent.getPsi.isInstanceOf[ScArgumentExprList] ||
        treeParent.getPsi.isInstanceOf[ScPatternDefinition] ||
        treeParent.getPsi.isInstanceOf[ScVariableDefinition] ||
        treeParent.getPsi.isInstanceOf[ScFor] ||
        treeParent.getPsi.isInstanceOf[ScIf])) {
        psi match {
          case _: ScBlockExpr => descriptors add new FoldingDescriptor(node, nodeTextRange)
          case _ =>
        }
      }
      if (treeParent != null) {
        treeParent.getPsi match {
          case inf: ScInfixExpr if inf.right == node.getPsi =>
            psi match {
              case _: ScBlockExpr => descriptors add new FoldingDescriptor(node, nodeTextRange)
              case _ =>
            }
          case _ =>
        }
      }
      if (treeParent != null && treeParent.getPsi.isInstanceOf[ScCaseClause]) {
        psi match {
          case _: ScBlock => descriptors add new FoldingDescriptor(node, nodeTextRange)
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
    } else if (node.getElementType == ScalaTokenTypes.tLINE_COMMENT && !isWorksheetResults(node)) {
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

  override def buildLanguageFoldRegions(descriptors: java.util.List[FoldingDescriptor], root: PsiElement, document: Document,
                               quick: Boolean): Unit = {
    val processedComments = new mutable.HashSet[PsiElement]
    val processedRegions = new mutable.HashSet[PsiElement]
    appendDescriptors(root.getNode, document, descriptors, processedComments, processedRegions)
  }

  override def getLanguagePlaceholderText(node: ASTNode, textRange: TextRange): String = {
    if (isMultiline(node) || isMultilineImport(node) && !isWorksheetResults(node)) {
      node.getElementType match {
        case ScCodeBlockElementType.BlockExpression => return "{...}"
        case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
        case ScalaDocElementTypes.SCALA_DOC_COMMENT => return "/**...*/"
        case TEMPLATE_BODY => return "{...}"
        case PACKAGING => return "{...}"
        case ImportStatement => return "..."
        case MATCH_STMT => return "{...}"
        case ScalaTokenTypes.tSH_COMMENT if node.getText.charAt(0) == ':' => return "::#!...::!#"
        case ScalaTokenTypes.tSH_COMMENT => return "#!...!#"
        case FUNCTION_DEFINITION =>
          val (isMultilineBody, _, sign) = isMultilineFuncBody(node.getPsi.asInstanceOf[ScFunctionDefinition])
          if (isMultilineBody) return sign
        case _ =>
      }
      if (node.getPsi != null) {
        node.getPsi match {
          case literal: ScLiteral if literal.isMultiLineString => return "\"\"\"...\"\"\""
          case _ =>
        }
        if (node.getPsi.isInstanceOf[ScArgumentExprList])
          return "(...)"
      }
    }
    if (node.getTreeParent != null && (ARG_EXPRS == node.getTreeParent.getElementType
      || INFIX_EXPR == node.getTreeParent.getElementType
      || PATTERN_DEFINITION == node.getTreeParent.getElementType
      || VARIABLE_DEFINITION == node.getTreeParent.getElementType)) {
      node.getPsi match {
        case _: ScBlockExpr => return "{...}"
        case _ => return null
      }
    }
    node.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT =>
        if (!isWorksheetResults(node)) {
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
    if (parentElementType.isInstanceOf[ScStubFileElementType] &&
      node.getTreePrev == null && node.getElementType != PACKAGING &&
            foldingSettings.isCollapseFileHeaders) true
    else if (parentElementType.isInstanceOf[ScStubFileElementType] &&
      node.getElementType == ImportStatement &&
            foldingSettings.isCollapseImports) true
    else if (parent != null &&
      PATTERN_DEFINITION == parentElementType &&
            foldingSettings.isCollapseMultilineBlocks) true
    else if (parent != null &&
      VARIABLE_DEFINITION == parentElementType &&
            foldingSettings.isCollapseMultilineBlocks) true
    else {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT
          if foldingSettings.isCollapseBlockComments && !isWorksheetResults(node) => true
        case ScalaTokenTypes.tLINE_COMMENT
          if !isCustomRegionStart(node.getText) &&
                  foldingSettings.isCollapseLineComments && !isWorksheetResults(node) => true
        case ScalaTokenTypes.tLINE_COMMENT
          if isCustomRegionStart(node.getText) &&
                  foldingSettings.isCollapseCustomRegions => true
        case ScalaDocElementTypes.SCALA_DOC_COMMENT
          if foldingSettings.isCollapseScalaDocComments && !isWorksheetResults(node) => true
        case TEMPLATE_BODY
          if foldingSettings.isCollapseTemplateBodies => true
        case PACKAGING
          if foldingSettings.isCollapsePackagings => true
        case ImportStatement
          if foldingSettings.isCollapseImports => true
        case ScalaTokenTypes.tSH_COMMENT
          if foldingSettings.isCollapseShellComments && !isWorksheetResults(node) => true
        case MATCH_STMT
          if foldingSettings.isCollapseMultilineBlocks => true
        case ScCodeBlockElementType.BlockExpression
          if foldingSettings.isCollapseMultilineBlocks => true
        case SIMPLE_TYPE => true
        case _ if psi.isInstanceOf[ScBlockExpr] &&
          parentElementType == ARG_EXPRS &&
                foldingSettings.isCollapseMethodCallBodies => true
        case _ if parentElementType == FUNCTION_DEFINITION &&
                foldingSettings.isCollapseMethodCallBodies &&
          isMultilineFuncBody(parent.getPsi.asInstanceOf[ScFunctionDefinition])._1 => true
        case _ if psi.isInstanceOf[ScTypeProjection] &&
                foldingSettings.isCollapseTypeLambdas => true
        case _ if psi.isInstanceOf[ScTypeElement] &&
                foldingSettings.isCollapseTypeLambdas => true
        case _ if psi.isInstanceOf[ScLiteral] &&
          psi.asInstanceOf[ScLiteral].isMultiLineString &&
                foldingSettings.isCollapseMultilineStrings => true
        case _ if psi.isInstanceOf[ScArgumentExprList] &&
                foldingSettings.isCollapseMultilineBlocks => true
        case _ => false
      }
    }
  }

  private def isMultiline(node: ASTNode): Boolean = {
    node.getText.indexOf("\n") != -1
  }

  private def isMultilineBodyInMatchStmt(node: ASTNode): Boolean = {
    val children = node.getPsi.asInstanceOf[ScMatch].children
    var index = 0
    for (ch <- children) {
      if (ch.isInstanceOf[PsiElement] && ch.getNode.getElementType == ScalaTokenTypes.kMATCH) {
        val result = node.getText.substring(index + MATCH_KEYWORD.length)
        return result.indexOf("\n") != -1
      } else {
        index += ch.getTextLength
      }
    }
    false
  }

  private def startOffsetForMatchStmt(node: ASTNode): Int = {
    val children = node.getPsi.asInstanceOf[ScMatch].children
    var offset = 0
    var passedMatch = false
    for (ch <- children) {
      if (ch.isInstanceOf[PsiElement] && ch.getNode.getElementType == ScalaTokenTypes.kMATCH) {
        offset += MATCH_KEYWORD.length
        passedMatch = true
      } else if (passedMatch) {
        if (ch.isInstanceOf[PsiElement] && ch.getNode.getElementType == TokenType.WHITE_SPACE) offset += ch.getTextLength
        return offset
      } else {
        offset += ch.getTextLength
      }
    }
    0
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

  private def isMultilineFuncBody(func: ScFunctionDefinition): (Boolean, TextRange, String) = {
    val body = func.body.orNull
    if (body == null) return (false, null, "")
    val range = body.getTextRange
    body match {
      case _: ScBlockExpr =>
        val isCorrectRange = range.getStartOffset + 1 < range.getEndOffset
        return (isCorrectRange, range, "{...}")
      case _ =>
        val isMultilineBody = (body.getText.indexOf("\n") != -1) && (range.getStartOffset + 1 < range.getEndOffset)
        val textRange = if (isMultilineBody) range else null
        return (isMultilineBody, textRange, "...")
    }
    (false, null, "")
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
        if (elementType == ScalaTokenTypes.tLINE_COMMENT  && !isWorksheetResults(node)) {
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
        if (elementType == ScalaTokenTypes.tLINE_COMMENT && isCustomRegionEnd(node.getText)  && !isWorksheetResults(node)) {
          if ((isTagRegion && isTagRegionEnd(node.getText)) || (!isTagRegion && isSimpleRegionEnd(node.getText))) {
            if (!processedRegions.contains(current) && stack.isEmpty) {
              end = current
              processedRegions.add(current)
              flag = false
            }
          }
          if (stack.nonEmpty) stack.pop()
        }
        if (elementType == ScalaTokenTypes.tLINE_COMMENT && isCustomRegionStart(node.getText)  && !isWorksheetResults(node)) {
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

  private def isTagRegionEnd(elementText: String): Boolean = {
    elementText.contains("</editor-fold")
  }

  private def isSimpleRegionEnd(elementText: String): Boolean = {
    elementText.contains("endregion")
  }

  private def isWorksheetResults(node: ASTNode): Boolean = {
    node.getPsi.isInstanceOf[PsiComment] && (node.getText.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX) ||
      node.getText.startsWith(WorksheetFoldingBuilder.LINE_PREFIX))
  }

  override def isDumbAware: Boolean = true
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
