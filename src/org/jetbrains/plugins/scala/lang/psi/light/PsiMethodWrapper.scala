package org.jetbrains.plugins.scala.lang.psi.light

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement.ElementScope
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil.javaTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alefas
  * @since 05.04.12
  */
abstract class PsiMethodWrapper(manager: PsiManager, method: PsiMethod, containingClass: PsiClass)
  extends LightMethod(manager, method, containingClass, containingClass.getLanguage) {

  implicit def elementScope: ElementScope = ElementScope(containingClass)

  protected def returnType: ScType

  protected def parameterListText: String

  override lazy val getReturnType: PsiType = Option(returnType).map(_.toPsiType()).orNull

  override lazy val getReturnTypeElement: PsiTypeElement = {
    val simpleTypeElem = method.getReturnTypeElement
    if (simpleTypeElem == null) null
    else {
      val fullTypeElem = Option(getReturnType)
        .map(javaTypeElement(_, method, manager.getProject))

      fullTypeElem.map(simpleTypeElem.replace(_).asInstanceOf[PsiTypeElement])
        .getOrElse(simpleTypeElem)
    }
  }

  override lazy val getParameterList: PsiParameterList = {
    val elementFactory = JavaPsiFacade.getInstance(getProject).getElementFactory
    val dummyMethod = elementFactory.createMethodFromText(s"void method$parameterListText", method)

    method.getParameterList.replace(dummyMethod.getParameterList).asInstanceOf[PsiParameterList]
  }

  override def getSignature(substitutor: PsiSubstitutor): MethodSignature = {
    getParameterList
    getReturnTypeElement
    method.getSignature(substitutor)
  }

  override final def getNavigationElement: PsiElement =
    super.getNavigationElement

  override final def setNavigationElement(navigationElement: PsiElement): Unit =
    super.setNavigationElement(navigationElement)

  override final def getParent: PsiElement =
    containingClass

  override final def findDeepestSuperMethods(): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

  override final def findDeepestSuperMethod(): PsiMethod =
    PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

  override final def findSuperMethods(): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethods(this)

  override final def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess)

  override final def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethods(this, parentClass)

  override final def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): util.List[MethodSignatureBackedByPsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

  override final def getHierarchicalMethodSignature: HierarchicalMethodSignature =
    PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)
}

trait NavigablePsiElementWrapper extends NavigatablePsiElement {
  val delegate: NavigatablePsiElement

  override final def navigate(requestFocus: Boolean): Unit =
    delegate.navigate(requestFocus)

  override final def canNavigate: Boolean =
    delegate.canNavigate

  override final def canNavigateToSource: Boolean =
    delegate.canNavigateToSource

  override def getPrevSibling: PsiElement =
    delegate.getPrevSibling

  override def getNextSibling: PsiElement =
    delegate.getNextSibling

  override def getTextRange: TextRange =
    delegate.getTextRange

  override def getTextOffset: Int =
    delegate.getTextOffset
}