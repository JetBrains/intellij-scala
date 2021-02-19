package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Alexander Podkhalyuzin
 * Date: 07.03.2008
 */

trait ScAnnotationBase extends ScalaPsiElementBase with PsiAnnotation { this: ScAnnotation =>
  /**
   * Return full annotation only without @ token.
   * @return annotation expression
   */
  def annotationExpr: ScAnnotationExpr

  /**
   * Return constructor element af annotation expression. For example
   * if annotation is <code>@Nullable</code> then method returns <code>
   * Nullable</code> psiElement.
   * @return constructor element
   */
  def constructorInvocation: ScConstructorInvocation = annotationExpr.constructorInvocation

  def typeElement: ScTypeElement
}