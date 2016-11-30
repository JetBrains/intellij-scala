package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.project.DumbService
import com.intellij.psi.{PsiAnnotation, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

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
    def hasMetaAnnotation(results: Array[ResolveResult]) = results.map(_.getElement).exists {
      case c: ScPrimaryConstructor => c.containingClass.isMetaAnnotatationImpl
      case o: ScTypeDefinition => o.isMetaAnnotatationImpl
      case _ => false
    }
    // do not resolve anything while the stubs are building to avoid deadlocks
    if (ScTemplateDefinitionElementType.isStubBuilding.get() || DumbService.isDumb(getProject))
      return false

    constructor.reference.exists {
      case stRef: ScStableCodeReferenceElementImpl =>
        val processor = new ResolveProcessor(stRef.getKinds(incomplete = false), stRef, stRef.refName)
        hasMetaAnnotation(stRef.doResolve(stRef, processor))
      case _ => false
    }
  }

  @volatile
  var strip = false
}