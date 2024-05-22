package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

private sealed trait ScalaPsiEventFilter {
  def shouldSkip(event: PsiTreeChangeEvent): Boolean = false

  def shouldSkip(changedElement: PsiElement): Boolean = false

  def shouldSkip(newElement: Option[PsiElement], oldElement: Option[PsiElement]): Boolean =
    newElement.forall(shouldSkip) && oldElement.forall(shouldSkip)
}

private object ScalaPsiEventFilter {
  val defaultFilters: Seq[ScalaPsiEventFilter] =
    Seq(InternalEventsFilter, NonSignificantParentsFilter, IgnoreLeafElementsFilter)

  private object InternalEventsFilter extends ScalaPsiEventFilter {
    override def shouldSkip(event: PsiTreeChangeEvent): Boolean =
      isGenericChange(event) || isFromIdeaInternalFile(event)

    private def isFromIdeaInternalFile(event: PsiTreeChangeEvent) = {
      val vFile = event.getFile match {
        case null => event.getOldValue.asOptionOf[VirtualFile]
        case file =>
          val fileType = file.getFileType
          if (fileType == ScalaFileType.INSTANCE || fileType == JavaFileType.INSTANCE) None
          else Option(file.getVirtualFile)
      }
      vFile.exists(ProjectUtil.isProjectOrWorkspaceFile)
    }

    private def isGenericChange(event: PsiTreeChangeEvent) = event match {
      case impl: PsiTreeChangeEventImpl => impl.isGenericChange
      case _ => false
    }
  }

  private object NonSignificantParentsFilter extends ScalaPsiEventFilter {
    override def shouldSkip(changedElement: PsiElement): Boolean = {
      // do not update on changes in dummy file or comments
      PsiTreeUtil.getParentOfType(changedElement, classOf[ScalaCodeFragment], classOf[PsiComment]) != null
    }
  }

  private object IgnoreLeafElementsFilter extends ScalaPsiEventFilter {

    import ScalaTokenTypes._
    import TokenType._

    private val significantTokens: TokenSet = {
      TokenSet.orSet(IDENTIFIER_TOKEN_SET, KEYWORDS, LITERALS, TokenSet.create(tUNDER))
    }

    //consider last dot and underscore in import expressions as significant
    private def isInImportExpression(validElement: PsiElement): Boolean =
      validElement.is[ScImportExpr] || validElement.getParent.is[ScImportExpr]

    override def shouldSkip(changedElement: PsiElement): Boolean = {
      changedElement match {
        case leaf: LeafPsiElement if isInImportExpression(leaf) => false
        case leaf: LeafPsiElement =>
          leaf.getElementType match {
            case WHITE_SPACE | BAD_CHARACTER => true
            case scalaToken: ScalaTokenType  => !significantTokens.contains(scalaToken)
            case _                           => false
          }
        case _: PsiErrorElement              => true
        case _                               => false
      }
    }
  }
}
