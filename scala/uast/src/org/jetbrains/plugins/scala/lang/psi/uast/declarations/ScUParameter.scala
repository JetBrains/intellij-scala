package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import _root_.java.util

import com.intellij.psi.impl.light.LightParameter
import com.intellij.psi.{
  PsiElement,
  PsiExpression,
  PsiModifier,
  PsiParameter,
  PsiType
}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnchorOwner,
  ScUAnnotated,
  ScUElement
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.uast._

/**
  * [[ScParameter]] adapter for the [[UParameter]]
  *
  * @param scElement Scala PSI element representing parameter (e.g. of class or function)
  */
class ScUParameter(override protected val scElement: ScParameter,
                   override protected val parent: LazyUElement)
    extends UParameterAdapter(scElement)
    with ScUElement
    with ScUAnchorOwner
    with ScUAnnotated {

  override type PsiFacade = PsiParameter
  override protected val namedElement: ScNamedElement = scElement

  @Nullable
  override def getTypeReference: UTypeReferenceExpression =
    scElement.typeElement
      .flatMap(_.convertTo[UTypeReferenceExpression](this))
      .orNull

  @Nullable
  override def getUastInitializer: UExpression =
    scElement.getDefaultExpression
      .flatMap(_.convertTo[UExpression](this))
      .orNull

  @Nullable
  override def getInitializer: PsiExpression = scElement.getInitializer
}

/**
  * [[UParameter]] implementation based on light PSI parameters.
  * It is primarily used for Scala lambdas without explicitly specified
  * parameters (e.g. for underscore lambdas).
  */
class ScULambdaParameter(private val psiParameter: PsiParameter,
                         sourcePsi: Option[PsiElement],
                         override protected val parent: LazyUElement)
    extends UParameterAdapter(psiParameter)
    with ScUElement {

  def this(name: String,
           psiType: PsiType,
           declarationScope: PsiElement,
           sourcePsi: Option[PsiElement],
           parent: LazyUElement) =
    this({
      val tmp = new LightParameter(name, psiType, declarationScope)
      tmp.setModifiers(PsiModifier.FINAL)
      tmp
    }, sourcePsi, parent)

  override type PsiFacade = PsiParameter

  override protected val scElement: PsiFacade = psiParameter

  @Nullable
  override def getSourcePsi: PsiElement = sourcePsi.orNull

  @Nullable
  override def getTypeReference: UTypeReferenceExpression = null

  @Nullable
  override def getUastInitializer: UExpression = null

  // escapes looping caused by the default implementation
  override def getUAnnotations: util.List[UAnnotation] =
    JavaCollectionsCommon.newEmptyJavaList

  @Nullable
  override def getUastAnchor: UElement =
    sourcePsi.map(createUIdentifier(_, this)).orNull
}
