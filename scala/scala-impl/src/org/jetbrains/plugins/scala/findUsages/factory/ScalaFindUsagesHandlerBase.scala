package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesHandler, FindUsagesOptions}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Context
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

class ScalaFindUsagesHandlerBase(
  element: PsiElement,
  config: ScalaFindUsagesConfiguration
) extends FindUsagesHandler(element) {
  private[this] def applyFactoryMethods(cls: ScClass): Seq[PsiMethod] = {
    val companion    = ScalaPsiUtil.getCompanionModule(cls)
    val applyMethods = companion.toSeq.flatMap(_.allFunctionsByName("apply"))

    applyMethods.filter {
      case f: ScFunctionDefinition if f.isApplyMethod => f.isSynthetic
      case f: ScFunctionDefinition =>
        implicit val context: Context = Context(f)

        val returnType = f.returnType.toOption
        returnType.exists(_.equiv(ScDesignatorType(cls)))
      case _ => false
    }
  }

  override def getPrimaryElements: Array[PsiElement] =
    element match {
      case c: ScClass if config.getTypeDefinitionOptions.isOnlyNewInstances =>
        val constructors = c.constructors
        (constructors ++ applyFactoryMethods(c)).toArray
      case _ => Array(element)
    }

  override def getFindUsagesOptions(dataContext: DataContext): FindUsagesOptions = {
    val options = config.getFindUsagesOptionsResolver(element).getOptions
    options.getOrElse(super.getFindUsagesOptions(dataContext))
  }

  override def getFindUsagesDialog(
    isSingleFile:     Boolean,
    toShowInNewTab:   Boolean,
    mustOpenInNewTab: Boolean
  ): AbstractFindUsagesDialog = {
    config
      .getFindUsagesOptionsResolver(element)
      .getDialog(this, getProject, isSingleFile, toShowInNewTab, mustOpenInNewTab)
      .getOrElse(super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab))
  }
}
