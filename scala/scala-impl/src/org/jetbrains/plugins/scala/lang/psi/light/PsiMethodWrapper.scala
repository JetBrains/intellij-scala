package org.jetbrains.plugins.scala.lang.psi.light

import java.util

import com.intellij.psi._
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light._
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.util.HashBuilder._

abstract class PsiMethodWrapper[T <: ScalaPsiElement with PsiNamedElement with NavigatablePsiElement](
  override val delegate: T,
  methodName: String,
  containingClass: PsiClass
) extends LightMethodBuilder(delegate.getManager, containingClass.getLanguage, methodName)
  with NavigablePsiElementWrapper[T] {

  implicit def elementScope: ElementScope = ElementScope(containingClass)

  setContainingClass(containingClass)

  @volatile private var _returnType: PsiType = NullPsiType

  @volatile private var _typeParameterList: PsiTypeParameterList = _

  @volatile private var _modifierList: PsiModifierList = _

  @volatile private var _parameterList: PsiParameterList = _

  @volatile private var _throwsList: PsiReferenceList = _

  protected def returnScType: ScType

  protected def parameters: collection.Seq[PsiParameter]

  protected def typeParameters: Seq[PsiTypeParameter]

  protected def modifierList: PsiModifierList

  override def getThrowsList: PsiReferenceList = {
    if (_throwsList == null) {
      _throwsList = ScLightThrowsList(delegate)
    }
    _throwsList
  }

  override def getModifierList: PsiModifierList = {
    if (_modifierList == null) {
      _modifierList = modifierList
    }
    _modifierList
  }

  override def getTypeParameterList: PsiTypeParameterList = {
    if (_typeParameterList == null) {
      _typeParameterList = typeParameterList
    }
    _typeParameterList
  }

  override def getReturnType: PsiType = {
    if (_returnType == NullPsiType) {
      _returnType = returnType
    }
    _returnType
  }

  override def getParameterList: PsiParameterList = {
    if (_parameterList == null) {
      _parameterList = parameterList
    }
    _parameterList
  }

  private def returnType = Option(returnScType).map(_.toPsiType).orNull

  private def parameterList: PsiParameterList = new ScLightParameterList(myManager, containingClass.getLanguage, parameters)

  private def typeParameterList: PsiTypeParameterList = {
    val list = new LightTypeParameterListBuilder(myManager, getLanguage)
    typeParameters.foreach(list.addParameter)
    list
  }

  override def getSignature(substitutor: PsiSubstitutor): MethodSignature = {
    MethodSignatureBackedByPsiMethod.create(this, substitutor)
  }

  override final def getParent: PsiElement = containingClass

  override final def findDeepestSuperMethods(): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

  override final def findDeepestSuperMethod(): PsiMethod =
    PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

  override def findSuperMethods(): Array[PsiMethod] =
    PsiMethod.EMPTY_ARRAY

  override final def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] =
    if (!checkAccess) findSuperMethods()
    else findSuperMethods().filterNot(_.hasModifierPropertyScala(PsiModifier.PRIVATE))

  override final def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] =
    findSuperMethods().filter(_.getContainingClass == parentClass)

  override final def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): util.List[MethodSignatureBackedByPsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

  override final def getHierarchicalMethodSignature: HierarchicalMethodSignature =
    PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)

  override def equals(other: Any): Boolean = other match {
    case that: PsiMethodWrapper[_] =>
      that.getName == getName &&
        that.delegate == delegate &&
        that.getContainingClass == getContainingClass
    case _ => false
  }

  override def hashCode(): Int =
    getName #+ delegate #+ getContainingClass
}

object PsiMethodWrapper {
  def containingClass(delegate: ScNamedElement, cClass: Option[PsiClass], isStatic: Boolean): PsiClass = {
    val result = cClass.getOrElse {
      delegate.nameContext match {
        case s: ScMember =>
          val res = s.containingClass match {
            case null => s.syntheticContainingClass
            case clazz => clazz
          }
          if (isStatic) {
            res match {
              case o: ScObject => o.fakeCompanionClassOrCompanionClass
              case _ => res
            }
          } else res
        case _ => null
      }
    }

    assert(
      result != null,
      s"""Member: ${delegate.getText}
         |has null containing class. isStatic: $isStatic
         |Containing file text: ${delegate.getContainingFile.getText}""".stripMargin
    )

    result
  }
}