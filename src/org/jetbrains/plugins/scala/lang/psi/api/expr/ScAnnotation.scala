package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotation extends ScalaPsiElement with PsiAnnotation {
  /**
   * Return full annotation only without @ token.
   * @return annotation expression
   */
  def annotationExpr: ScAnnotationExpr = findChildByClassScala(classOf[ScAnnotationExpr])

  /**
   * Return constructor element af annotation expressison. For example
   * if annotation is <code>@Nullable</code> then method returns <code>
   * Nullable</code> psiElement.
   * @return constructor element
   */
  def constructor = annotationExpr.constr

  /**
   * Return attributes for current annotation (NameValuePair).
   * @return annotation attributes.
   */
  def attributes = annotationExpr.getAttributes
}