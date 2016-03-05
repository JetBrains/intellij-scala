package org.jetbrains.plugins.hocon.ref

import com.intellij.psi.{ElementManipulators, PsiElement, PsiReference}
import org.jetbrains.plugins.hocon.psi.HKey

/**
  * @author ghik
  */
class HKeySelfReference(key: HKey) extends PsiReference {
  def getVariants = Array.empty

  def getCanonicalText = key.stringValue

  def getElement: PsiElement = key

  def isReferenceTo(element: PsiElement) =
    element == resolve()

  def bindToElement(element: PsiElement): PsiElement = null

  def handleElementRename(newElementName: String): PsiElement =
    ElementManipulators.getManipulator(key).handleContentChange(key, newElementName)

  def isSoft = true

  def getRangeInElement = ElementManipulators.getValueTextRange(key)

  def resolve(): PsiElement = key
}
