package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.refactoring.RefactoringBundle

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaIntroduceParameterViewDescriptor(methodToSearchFor: PsiMethod) extends UsageViewDescriptorAdapter {
  def getElements: Array[PsiElement] = Array(methodToSearchFor)
  def getProcessedElementsHeader: String = {
    return RefactoringBundle.message("introduce.parameter.elements.header")
  }
}