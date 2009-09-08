package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.psi.PsiElement
import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory {
  def canFindUsages(element: PsiElement): Boolean = {
    element match {
      case _: FakePsiMethod => true
      case _ => false
    }
  }

  def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    new ScalaFindUsagesHandler(element)
  }
}