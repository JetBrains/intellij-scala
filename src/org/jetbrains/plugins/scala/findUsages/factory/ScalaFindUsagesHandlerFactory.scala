package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.{PsiMethod, PsiElement}
import com.intellij.find.findUsages.{FindUsagesHandlerFactory, JavaFindUsagesHandlerFactory, JavaFindUsagesHandler, FindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.light._

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory {
  override def canFindUsages(element: PsiElement): Boolean = {
    element match {
      case _: FakePsiMethod => true
      case _: ScTypedDefinition => true
      case _: ScTypeDefinition => true
      case _: PsiClassWrapper => true
      case _: ScFunctionWrapper => true
      case _: StaticPsiMethodWrapper => true
      case _: PsiTypedDefinitionWrapper => true
      case _: StaticPsiTypedDefinitionWrapper => true
      case _ => false
    }
  }

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    new ScalaFindUsagesHandler(element)
  }
}