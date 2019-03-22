package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesHandler, FindUsagesOptions}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

class ScalaFindUsagesHandlerBase(
  element: PsiElement,
  factory: ScalaFindUsagesHandlerFactory
) extends FindUsagesHandler(element) {
  private[this] def applyFactoryMethods(cls: ScClass): Seq[PsiMethod] = {
    val companion    = ScalaPsiUtil.getCompanionModule(cls)
    val applyMethods = companion.toSeq.flatMap(_.allFunctionsByName("apply"))

    applyMethods.filter {
      case f: ScFunctionDefinition if f.isApplyMethod => f.isSynthetic
      case f: ScFunctionDefinition =>
        val returnType = f.returnType.toOption
        returnType.exists(_.equiv(ScDesignatorType(cls)))
      case _ => false
    }
  }

  override def getPrimaryElements: Array[PsiElement] =
    element match {
      case c: ScClass if factory.typeDefinitionOptions.isOnlyNewInstances =>
        val constructors = c.constructors
        (constructors ++ applyFactoryMethods(c)).toArray
      case _ => Array(element)
    }

  override def getFindUsagesOptions(dataContext: DataContext): FindUsagesOptions =
    element match {
      case _: ScTypeDefinition                      => factory.typeDefinitionOptions
      case inNameContext(m: ScMember) if !m.isLocal => factory.memberOptions
      case _: ScParameter | _: ScTypeParam |
          inNameContext(_: ScMember | _: ScCaseClause | _: ScGenerator | _: ScForBinding) =>
        factory.localOptions
      case _ => super.getFindUsagesOptions(dataContext)
    }

  override def getFindUsagesDialog(
    isSingleFile:     Boolean,
    toShowInNewTab:   Boolean,
    mustOpenInNewTab: Boolean
  ): AbstractFindUsagesDialog =
    element match {
      case t: ScTypeDefinition =>
        new ScalaTypeDefinitionUsagesDialog(
          t,
          getProject,
          getFindUsagesOptions,
          toShowInNewTab,
          mustOpenInNewTab,
          isSingleFile,
          this
        )
      case _ => super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
}
