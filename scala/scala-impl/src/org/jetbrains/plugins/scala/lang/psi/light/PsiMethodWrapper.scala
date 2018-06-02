package org.jetbrains.plugins.scala.lang.psi.light

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil.javaTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alefas
  * @since 05.04.12
  */
abstract class PsiMethodWrapper(manager: PsiManager, method: PsiMethod, containingClass: PsiClass)
  extends LightMethod(manager, method, containingClass, containingClass.getLanguage) {

  implicit def elementScope: ElementScope = ElementScope(containingClass)

  private var retTypeElement: Option[PsiTypeElement] = None
  private var paramList: Option[PsiParameterList] = None
  private var retType: Option[PsiType] = None

  protected def returnType: ScType

  protected def parameterListText: String

  override def getReturnType: PsiType = {
    retType.getOrElse {
      val computed = Option(returnType).map(_.toPsiType).orNull
      retType = Some(computed)
      retType.orNull
    }
  }

  override def getReturnTypeElement: PsiTypeElement = {
    retTypeElement.getOrElse {
      updateRetTypeElement()
      retTypeElement.orNull
    }
  }

  override def getParameterList: PsiParameterList = {
    paramList.getOrElse {
      updateParamList()
      paramList.orNull
    }
  }

  private def updateRetTypeElement(): Unit = {
    //`getReturnType` inside synchronized may lead to a deadlock
    val fullTypeElem = Option(getReturnType)
      .map(javaTypeElement(_, method, manager.getProject))

    //we update not only retTypeElement, but also psi of `method`
    synchronized {
      if (retTypeElement.isEmpty) {
        val simpleTypeElem = method.getReturnTypeElement
        if (simpleTypeElem != null) {
          val newTypeElem =
            fullTypeElem.map(simpleTypeElem.replace(_).asInstanceOf[PsiTypeElement])
              .getOrElse(simpleTypeElem)

          retTypeElement = Some(newTypeElem)
        }
      }
    }
  }

  private def updateParamList(): Unit = {
    val elementFactory = JavaPsiFacade.getInstance(getProject).getElementFactory
    //`parameterListText` is heavy operation without side effects, so it should be outside synchronized
    val dummyMethod = elementFactory.createMethodFromText(s"void method$parameterListText", method)

    synchronized {
      if (paramList.isEmpty) {
        val newParamLis = method.getParameterList.replace(dummyMethod.getParameterList).asInstanceOf[PsiParameterList]
        paramList = Some(newParamLis)
      }
    }
  }

  override def getSignature(substitutor: PsiSubstitutor): MethodSignature = {
    updateParamList()
    updateRetTypeElement()
    method.getSignature(substitutor)
  }

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

trait NavigablePsiElementWrapper[E <: NavigatablePsiElement] extends NavigatablePsiElement {
  val delegate: E

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