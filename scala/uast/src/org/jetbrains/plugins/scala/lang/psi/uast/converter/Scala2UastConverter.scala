package org.jetbrains.plugins.scala.lang.psi.uast.converter

import com.intellij.psi.{PsiComment, PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.uast._

import scala.reflect.ClassTag

trait Scala2UastConverter { converter =>

  //region Abstract members
  def convertTo[U <: UElement: ClassTag: NotNothing](
    element: PsiElement,
    @Nullable parent: UElement,
    /**/ convertLambdas: Boolean = true
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
  def convert(element: PsiElement,
              @Nullable parent: UElement,
              convertLambdas: Boolean = true): Option[UElement] =
    convertTo[UElement](element, parent, convertLambdas)

  def convertWithParent(element: PsiElement,
                        convertLambdas: Boolean = true): Option[UElement] =
    convertWithParentTo[UElement](element, convertLambdas)

  def convertToUExpressionOrEmpty(element: PsiElement,
                                  @Nullable parent: UElement,
                                  convertLambdas: Boolean = true): UExpression =
    convertTo[UExpression](element, parent, convertLambdas)
      .getOrElse(createUEmptyExpression(element, parent))
  //endregion

  //region Common UAST element fabrics
  def createUEmptyExpression(@Nullable element: PsiElement,
                             @Nullable parent: UElement): UExpression =
    new UastEmptyExpression(parent)

  def createUIdentifier(@Nullable element: PsiElement,
                        @Nullable parent: UElement): UIdentifier =
    new UIdentifier(element, parent)

  def createUErrorType(): PsiType = UastErrorType.INSTANCE

  def createUComment(element: PsiComment,
                     @Nullable parent: UElement): UComment =
    new UComment(element, parent)
  //endregion

  //region Handy extensions for PSI elements
  implicit class UTypeable(private val scElement: Typeable) {
    def uastType(): PsiType =
      scElement.`type`().map(_.toPsiType).getOrElse(createUErrorType())
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
