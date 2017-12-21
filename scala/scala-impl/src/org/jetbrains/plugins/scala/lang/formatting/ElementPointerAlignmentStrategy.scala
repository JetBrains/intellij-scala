package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
  * @author Roman.Shein
  * @since 20.06.2016.
  */
class ElementPointerAlignmentStrategy(val rootPointer: SmartPsiElementPointer[PsiElement],
                                      val depthToParent: Int,
                                      val iElementType: Option[IElementType] = None,
                                      val additionalCondition: Option[(ASTNode) => Boolean] = None) {
  private val myAlignment = Alignment.createAlignment(true)

  def shouldAlign(node: ASTNode): Boolean = {
    if (iElementType.exists(iType => iType != node.getElementType)) return false
    var psiElement = node.getPsi()
    if (psiElement == null) return false
    if (rootPointer.getElement == null || rootPointer.getElement.getContainingFile != psiElement.getContainingFile) return false
    if (additionalCondition.exists(fun => !fun(node))) return false
    for (i <- 1 to depthToParent) {
      psiElement = psiElement.getParent
      if (psiElement == null) return false
    }
    rootPointer.getElement == psiElement
  }

  def getAlignment: Alignment = myAlignment
}

object ElementPointerAlignmentStrategy {
  def typeMultiLevelAlignment(root: PsiElement): ElementPointerAlignmentStrategy =
    new ElementPointerAlignmentStrategy(root.createSmartPointer, 2, Some(ScalaTokenTypes.tCOLON))
}