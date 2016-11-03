package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

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
   * Return constructor element af annotation expression. For example
   * if annotation is <code>@Nullable</code> then method returns <code>
   * Nullable</code> psiElement.
   * @return constructor element
   */
  def constructor: ScConstructor = annotationExpr.constr

  def typeElement: ScTypeElement

  def isMetaAnnotation: Boolean = {
    import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
    constructor.reference.exists {
      ref => ref.bind().exists {
        result => result.parentElement match {
          case Some(o: ScTypeDefinition) => o.isMetaAnnotatationImpl
          case _ => false
        }
      }
    }
  }

  @volatile
  var strip = false
}