package org.jetbrains.plugins.scala.lang.folding

import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, CodeStyleSettings}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.PsiWhiteSpace
import formatting.settings.ScalaCodeStyleSettings
import scaladoc.parser.ScalaDocElementTypes
import _root_.scala.collection.mutable._
import java.util.ArrayList;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.openapi.util._

/*
*
@author Ilya Sergey
*/

class ScalaFoldingBuilder extends FoldingBuilder {

  import ScalaFoldingUtil._

  private def appendDescriptors(node: ASTNode,
                               document: Document,
                               descriptors: ArrayBuffer[FoldingDescriptor]): Unit = {


    if (isMultiline(node) || isMultilineImport(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tSH_COMMENT | ScalaElementTypes.TEMPLATE_BODY |
                ScalaDocElementTypes.SCALA_DOC_COMMENT => descriptors += (new FoldingDescriptor(node, node.getTextRange))
        case ScalaElementTypes.PACKAGING => descriptors += (new FoldingDescriptor(node,
          new TextRange(node.getTextRange.getStartOffset + PACKAGE_KEYWORD.length + 1, node.getTextRange.getEndOffset)))
        case ScalaElementTypes.IMPORT_STMT if isGoodImport(node) => {
          descriptors += (new FoldingDescriptor(node,
            new TextRange(node.getTextRange.getStartOffset + IMPORT_KEYWORD.length + 1, getImportEnd(node))))
        }
        case ScalaElementTypes.MATCH_STMT => 
        case _ =>
      }
      if (node.getTreeParent() != null && node.getTreeParent().getPsi.isInstanceOf[ScFunction]) {
        node.getPsi match {
          case _: ScBlockExpr => descriptors += new FoldingDescriptor(node, node.getTextRange())
          case _ =>
        }
      }
    }
    for (ch <- node.getPsi.getChildren; val child = ch.getNode) {
      appendDescriptors(child, document, descriptors)
    }
  }

  def buildFoldRegions(astNode: ASTNode, document: Document): Array[FoldingDescriptor] = {
    var descriptors = new ArrayBuffer[FoldingDescriptor]
    appendDescriptors(astNode, document, descriptors);
    descriptors.toArray
  }

  def getPlaceholderText(node: ASTNode): String = {
    if (isMultiline(node) || isMultilineImport(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
        case ScalaDocElementTypes.SCALA_DOC_COMMENT => return "/**...*/"
        case ScalaElementTypes.TEMPLATE_BODY => return "{...}"
        case ScalaElementTypes.PACKAGING => return "{...}"
        case ScalaElementTypes.IMPORT_STMT => return "..."
        case ScalaTokenTypes.tSH_COMMENT if node.getText.charAt(0) == ':' => return "::#!...::!#"
        case ScalaTokenTypes.tSH_COMMENT => return "#!...!#"
        case _ =>
      }
    }
    if (node.getTreeParent() != null && ScalaElementTypes.FUNCTION_DEFINITION == node.getTreeParent().getElementType) {
      node.getPsi match {
        case _: ScBlockExpr => return "{...}"
        case _ => return null
      }
    }

    return null
  }

  def isCollapsedByDefault(node: ASTNode): Boolean = {
    val settings: ScalaCodeStyleSettings =
      CodeStyleSettingsManager.getSettings(node.getPsi.getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (node.getTreeParent.getElementType == ScalaElementTypes.FILE &&
            node.getTreePrev == null && node.getElementType != ScalaElementTypes.PACKAGING && settings.FOLD_FILE_HEADER) true
    else if (node.getTreeParent.getElementType == ScalaElementTypes.FILE && node.getElementType == ScalaElementTypes.IMPORT_STMT &&
      settings.FOLD_IMPORT_IN_HEADER) true
    else {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT if settings.FOLD_BLOCK_COMMENTS=> true
        case ScalaDocElementTypes.SCALA_DOC_COMMENT if settings.FOLD_SCALADOC=> true
        case ScalaElementTypes.TEMPLATE_BODY if settings.FOLD_TEMPLATE_BODIES=> true
        case ScalaElementTypes.PACKAGING if settings.FOLD_PACKAGINGS=> true
        case ScalaElementTypes.IMPORT_STMT if settings.FOLD_IMPORT_STATEMETS=> true
        case ScalaTokenTypes.tSH_COMMENT if settings.FOLD_SHELL_COMMENTS=> true
        case _ if node.getPsi.isInstanceOf[ScBlockExpr] && settings.FOLD_BLOCK => true
        case _ => false
      }
    }
  }

  private def isMultiline(node: ASTNode): Boolean = {
    return node.getText.indexOf("\n") != -1
  }

  private def isMultilineImport(node: ASTNode): Boolean = {
    if (node.getElementType != ScalaElementTypes.IMPORT_STMT) return false
    var next = node.getTreeNext
    var flag = false
    while (next != null && (next.getPsi.isInstanceOf[LeafPsiElement] || next.getElementType == ScalaElementTypes.IMPORT_STMT)) {
      if (next.getElementType == ScalaElementTypes.IMPORT_STMT) flag = true
      next = next.getTreeNext
    }
    return flag
  }

  private def isGoodImport(node: ASTNode): Boolean = {
    var prev = node.getTreePrev
    while (prev != null && prev.getPsi.isInstanceOf[LeafPsiElement]) prev = prev.getTreePrev
    if (prev == null || prev.getElementType != ScalaElementTypes.IMPORT_STMT) true
    else false
  }

  private def getImportEnd(node: ASTNode): Int = {
    var next = node
    var last = next.getTextRange.getEndOffset
    while (next != null && (next.getPsi.isInstanceOf[LeafPsiElement] || next.getElementType == ScalaElementTypes.IMPORT_STMT)) {
      if (next.getElementType == ScalaElementTypes.IMPORT_STMT || next.getElementType == ScalaTokenTypes.tSEMICOLON) last = next.getTextRange.getEndOffset
      next = next.getTreeNext
    }
    return last
  }
}

private[folding] object ScalaFoldingUtil {
  val IMPORT_KEYWORD = "import"
  val PACKAGE_KEYWORD = "package"
}
