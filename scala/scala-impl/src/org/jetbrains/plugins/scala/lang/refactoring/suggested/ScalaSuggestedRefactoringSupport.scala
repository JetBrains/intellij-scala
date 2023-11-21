package org.jetbrains.plugins.scala.lang.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.refactoring.suggested.{SuggestedRefactoringAvailability, SuggestedRefactoringExecution, SuggestedRefactoringStateChanges, SuggestedRefactoringSupport, SuggestedRefactoringUI}
import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

final class ScalaSuggestedRefactoringSupport extends SuggestedRefactoringSupport {
  // TODO(SCL-17973): Support Change Signature. Handle ScMethodLike
  override def isAnchor(element: PsiElement): Boolean = element match {
    //case _: ScParameter =>
    //  // As per parent JavaDoc: if Change Signature is supported then individual parameters must not be considered as anchors
    //  // for they are part of a bigger anchor (method or function)
    //  false
    case _: ScNamedElement => true
    case _                 => false
  }

  // TODO(SCL-17973): Support Change Signature. Get range inside ScMethodLike
  @Nullable
  override def signatureRange(element: PsiElement): TextRange = nameRange(element)

  // TODO(SCL-17973): Support Change Signature. Get range of existing imports or an empty range within whitespace where the imports are to be inserted
  @Nullable
  override def importsRange(file: PsiFile): TextRange = null

  @Nullable
  override def nameRange(element: PsiElement): TextRange = element match {
    case named: ScNamedElement =>
      ObjectUtils.doIfNotNull(named.getNameIdentifier, (_: PsiElement).getTextRange)
    case _ => null
  }

  override def isIdentifierStart(c: Char): Boolean = ScalaNamesUtil.isIdentifierStart(c)

  override def isIdentifierPart(c: Char): Boolean = ScalaNamesUtil.isIdentifierPart(c)

  // TODO(SCL-17973): Support Change Signature. Implement ScalaSuggestedRefactoringStateChanges
  override def getStateChanges: SuggestedRefactoringStateChanges =
    new SuggestedRefactoringStateChanges.RenameOnly(this)

  // TODO(SCL-17973): Support Change Signature. Implement ScalaSuggestedRefactoringAvailability
  override def getAvailability: SuggestedRefactoringAvailability =
    new SuggestedRefactoringAvailability.RenameOnly(this)

  // TODO(SCL-17973): Support Change Signature. Implement ScalaSuggestedRefactoringUI
  override def getUi: SuggestedRefactoringUI =
    SuggestedRefactoringUI.RenameOnly.INSTANCE

  // TODO(SCL-17973): Support Change Signature. Implement ScalaSuggestedRefactoringExecution
  override def getExecution: SuggestedRefactoringExecution =
    new SuggestedRefactoringExecution.RenameOnly(this)
}
