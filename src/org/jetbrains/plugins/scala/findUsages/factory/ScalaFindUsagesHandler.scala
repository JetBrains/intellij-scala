package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesOptions, FindUsagesHandler}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement,
                             typeDefinitionFindUsagesOptions: FindUsagesOptions) extends FindUsagesHandler(element) {
  override def isSearchForTextOccurencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean = {
    psiElement match {
      case _: ScTypeDefinition => true
      case _ => false
    }
  }
}