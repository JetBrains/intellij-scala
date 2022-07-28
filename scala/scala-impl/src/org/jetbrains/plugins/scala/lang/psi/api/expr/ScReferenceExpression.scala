package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

trait ScReferenceExpression extends ScExpression
  with ScReference {

  final def isQualified: Boolean = qualifier.isDefined

  override final def qualifier: Option[ScExpression] = getFirstChild.asOptionOf[ScExpression]

  def assignment: ScAssignment

  def assignment_=(statement: ScAssignment): Unit

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]

  /**
    * Includes qualifier for Infix, Postfix and Prefix expression
    *
    * @return qualifier for Infix, Postfix, Prefix or reference expression
    */
  final def smartQualifier: Option[ScExpression] = qualifier.orElse {
    getParent match {
      case ScSugarCallExpr(baseExpr, operation, _) if this == operation => Some(baseExpr)
      case _ => None
    }
  }

  /**
   * This method returns all possible types for this place.
   * It's useful for expressions, which has two or more valid resolve results.
   * For example scala package, and scala package object.
   * Another usecase is when our type inference failed to decide to which method
   * we should resolve. If all methods has same result type, then we will give valid completion and resolve.
   */
  def multiType: Array[TypeResult]

  /**
   * @return types in the same order as shapeResolve
   */
  def shapeMultiType: Array[TypeResult]

  def shapeResolve: Array[ScalaResolveResult]

  def shapeType: TypeResult

  def bindToElement(element: PsiElement, containingClass: Option[PsiClass]): PsiElement

  def getPrevTypeInfoParams: Seq[TypeParameter]
}

object ScReferenceExpression {

  def unapply(e: ScReferenceExpression): Option[PsiElement] = Option(e.resolve())

  object withQualifier {
    def unapply(exp: ScReferenceExpression): Option[ScExpression] = exp.qualifier
  }
}