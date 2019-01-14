package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor}

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReferenceExpression extends ScExpression
  with ScReferenceElement {

  final def isQualified: Boolean = qualifier.isDefined

  final def qualifier: Option[ScExpression] = getFirstChild match {
    case e: ScExpression => Some(e)
    case _ => None
  }

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

  override def createReplacingElementWithClassName(useFullQualifiedName: Boolean, clazz: TypeToImport): ScReferenceElement = {
    if (useFullQualifiedName) super.createReplacingElementWithClassName(useFullQualifiedName, clazz)
    else createExpressionFromText(clazz.name)(clazz.element.getManager).asInstanceOf[ScReferenceExpression]
  }

  def bindToElement(element: PsiElement, containingClass: Option[PsiClass]): PsiElement

  def getPrevTypeInfoParams: Seq[TypeParameter]

  def getSimpleVariants(incomplete: Boolean = true,
                        completion: Boolean = false,
                        implicits: Boolean = false): Seq[ScalaResolveResult] =
    doResolve(new CompletionProcessor(getKinds(incomplete, completion), this, isImplicit = implicits))
}

object ScReferenceExpression {

  def unapply(e: ScReferenceExpression): Option[PsiElement] = Option(e.resolve())

  object withQualifier {
    def unapply(exp: ScReferenceExpression): Option[ScExpression] = exp.qualifier
  }
}