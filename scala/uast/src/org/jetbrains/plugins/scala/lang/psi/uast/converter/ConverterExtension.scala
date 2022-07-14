package org.jetbrains.plugins.scala.lang.psi.uast.converter

import com.intellij.psi.{PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.uast._

import scala.reflect.ClassTag

/**
  * Provides different conversion variants and handy extensions for PSI elements
  */
trait ConverterExtension { converter: UastFabrics =>

  //region Abstract members
  def convertTo[U <: UElement: ClassTag: NotNothing](
    element: PsiElement,
    @Nullable parent: UElement,
    convertLambdas: Boolean = true
  ): Option[U]

  def convertWithParentTo[U <: UElement: ClassTag: NotNothing](
    element: PsiElement,
    convertLambdas: Boolean = true
  ): Option[U]

  def convertWithParentToUExpressionOrEmpty(
    element: PsiElement,
    convertLambdas: Boolean = true
  ): UExpression
  //endregion

  //region Common conversions
  def convertWithParent(element: PsiElement): Option[UElement] =
    convertWithParentTo[UElement](element, convertLambdas = false)

  def convertToUExpressionOrEmpty(element: PsiElement,
                                  @Nullable parent: UElement,
                                  convertLambdas: Boolean = true): UExpression =
    convertTo[UExpression](element, parent, convertLambdas)
      .getOrElse(createUEmptyExpression(element, parent))
  //endregion

  //region Extensions for PSI elements
  implicit class UTypeable(private val scElement: Typeable) {
    def uastType(): PsiType =
      scElement.`type`().mapToOption(_.toPsiType).getOrElse(createUErrorType())
  }

  implicit class UConvertible(private val psiElement: PsiElement) {

    def convertTo[U <: UElement: ClassTag: NotNothing](
      @Nullable parent: UElement,
      convertLambdas: Boolean = true
    ): Option[U] =
      converter.convertTo[U](psiElement, parent, convertLambdas)

    def convertWithParentTo[U <: UElement: ClassTag: NotNothing](
      convertLambdas: Boolean = true
    ): Option[U] =
      converter.convertWithParentTo[U](psiElement, convertLambdas)

    def convertToUExpressionOrEmpty(
      @Nullable parent: UElement,
      convertLambdas: Boolean = true
    ): UExpression =
      converter.convertToUExpressionOrEmpty(psiElement, parent, convertLambdas)

    def convertWithParentToUExpressionOrEmpty(
      convertLambdas: Boolean = true
    ): UExpression =
      converter.convertWithParentToUExpressionOrEmpty(
        psiElement,
        convertLambdas
      )
  }

  implicit class UConvertibleFromOpt(val psiElementOpt: Option[PsiElement]) {

    def convertToUExpressionOrEmpty(
      @Nullable parent: UElement,
      convertLambdas: Boolean = true
    ): UExpression =
      psiElementOpt
        .map(_.convertToUExpressionOrEmpty(parent, convertLambdas))
        .getOrElse(createUEmptyExpression(element = null, parent))

    def convertWithParentToUExpressionOrEmpty(
      convertLambdas: Boolean = true
    ): UExpression =
      psiElementOpt
        .map(_.convertWithParentToUExpressionOrEmpty(convertLambdas))
        .getOrElse(createUEmptyExpression(element = null, parent = null))
  }
  //endregion
}
