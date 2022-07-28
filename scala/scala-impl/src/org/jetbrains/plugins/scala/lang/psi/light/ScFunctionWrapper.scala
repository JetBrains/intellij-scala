package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

/**
 * Represents Scala functions for Java.
 */
class ScFunctionWrapper(override val delegate: ScFunction,
                        isStatic: Boolean,
                        isAbstract: Boolean,
                        cClass: Option[PsiClass],
                        isJavaVarargs: Boolean = false)
  extends PsiMethodWrapper(delegate, delegate.getName, PsiMethodWrapper.containingClass(delegate, cClass, isStatic)) {

  override def isConstructor: Boolean = delegate.isConstructor

  override protected def modifierList: PsiModifierList = {
    def isSyntheticMethodFromTrait = cClass.nonEmpty && delegate.containingClass.isInstanceOf[ScTrait]

    val isOverride = !isStatic && (isSyntheticMethodFromTrait || delegate.hasModifierProperty("override"))
    ScLightModifierList(delegate, isStatic, isAbstract, getContainingClass.isInstanceOf[ScTrait], isOverride)
  }

  override protected def parameters: Seq[PsiParameter] =
    delegate.effectiveParameterClauses
      .flatMap(_.effectiveParameters)
      .map(ScLightParameter.from(_, superSubstitutor.followed(methodTypeParamsSubstitutor), isJavaVarargs))

  override protected def typeParameters: Seq[PsiTypeParameter] =
    delegate.typeParameters.map(new ScLightTypeParam(_, superSubstitutor))

  override protected def returnScType: ScType = {
    val isConstructor = delegate.isConstructor
    if (isConstructor) null
    else {
      val originalReturnType = delegate.returnType.getOrAny
      superSubstitutor.followed(methodTypeParamsSubstitutor)(originalReturnType)
    }
  }

  private def superSubstitutor: ScSubstitutor = cClass match {
    case Some(td: ScTypeDefinition) =>
      td.methodsByName(delegate.name).find(_.method == delegate) match {
        case Some(sign) => sign.substitutor
        case _ => ScSubstitutor.empty
      }
    case _ => ScSubstitutor.empty
  }

  private def methodTypeParamsSubstitutor: ScSubstitutor =
    ScSubstitutor.bind(delegate.typeParameters, this.getTypeParameters.map(ScDesignatorType(_)))

  override def getNameIdentifier: PsiIdentifier = delegate.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String): PsiElement = {
    if (!delegate.isConstructor) delegate.setName(name)
    else this
  }

  override def isVarArgs: Boolean = isJavaVarargs

  override def findSuperMethods(): Array[PsiMethod] = {
    if (isStatic)
      return PsiMethod.EMPTY_ARRAY

    def wrap(superSig: PhysicalMethodSignature): PsiMethod = superSig.method match {
      case f: ScFunction => new ScFunctionWrapper(f, isStatic, isAbstract = f.isAbstractMember, cClass = None, isJavaVarargs)
      case m => m
    }

    val superSignatures =
      TypeDefinitionMembers.getSignatures(getContainingClass)
        .forName(delegate.name)
        .findNode(delegate)
        .map(_.supers.map(_.info).filterByType[PhysicalMethodSignature])

    superSignatures.getOrElse(Seq.empty).mapToArray(wrap)(PsiMethod.ARRAY_FACTORY)
  }

  override def copy(): PsiElement =
    new ScFunctionWrapper(delegate.copy().asInstanceOf[ScFunction], isStatic, isAbstract, cClass, isJavaVarargs)
}

object ScFunctionWrapper {
  def unapply(wrapper: ScFunctionWrapper): Option[ScFunction] = Some(wrapper.delegate)
}
