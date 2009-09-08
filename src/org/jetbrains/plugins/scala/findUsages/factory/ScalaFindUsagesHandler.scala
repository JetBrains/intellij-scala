package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesOptions, FindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement) extends {
    val replacedElement: PsiElement = {
      element match {
        case f: FakePsiMethod => f.navElement
        case _ => element
      }
    }
  } with FindUsagesHandler(replacedElement) {
}