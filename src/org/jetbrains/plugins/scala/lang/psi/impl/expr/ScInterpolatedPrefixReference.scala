package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.{PsiPolyVariantReference, ResolveResult, PsiElement, PsiNamedElement}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolated

/**
 * @author kfeodorov 
 * @since 09.03.14.
 */
class ScInterpolatedPrefixReference(node: ASTNode) extends ScReferenceExpressionImpl(node) {

  override def multiResolve(incomplete: Boolean): Array[ResolveResult]  = {
    val parent = getParent match {
      case p: ScInterpolated => p
      case _ => return Array[ResolveResult]()
    }

    parent.getStringContextExpression match {
      case Some(expr) => expr.getFirstChild.getLastChild.findReferenceAt(0) match {
        case ref: PsiPolyVariantReference =>
          val resolve1 = ref.multiResolve(incomplete)
          resolve1.filter(_.getElement.isInstanceOf[ScFunction])
        case _ => Array[ResolveResult]()
      }
      case _ => Array[ResolveResult]()
    }
  }

  private def psiElement = node.getPsi

  override def advancedResolve: Option[ScalaResolveResult] = {
    if (resolve() != null) Some(new ScalaResolveResult(resolve().asInstanceOf[PsiNamedElement])) else None
  }

  override def nameId: PsiElement = getFirstChild

  override def getElement = this

  override def getRangeInElement: TextRange = new TextRange(0, node.getTextLength)

  override def resolve(): PsiElement = {
    val results = multiResolve(false)
    if (results.length == 1) results(0).asInstanceOf[ScalaResolveResult].element else null
  }

  override def getCanonicalText: String = node.getText

  override def handleElementRename(newElementName: String): PsiElement = {
    psiElement.replace(ScalaPsiElementFactory.createInterpolatedStringPrefix(newElementName, psiElement.getManager))
  }

  override def bindToElement(element: PsiElement): PsiElement = this

  override def isReferenceTo(element: PsiElement): Boolean = resolve() == element && element != null

  override def getVariants: Array[AnyRef] = if (resolve() != null) Array[AnyRef](resolve()) else Array[AnyRef]()

  override def isSoft: Boolean = false

  override def expectedType(fromUnderscore: Boolean): Option[ScType] = resolve() match {
    case f: ScFunction =>
      f.returnType match {
        case Success(result, _) => Option(result)
        case _ => super.expectedType(fromUnderscore)
      }
    case _ => super.expectedType(fromUnderscore)
  }

  override def expectedTypeEx(fromUnderscore: Boolean): Option[(ScType, Option[ScTypeElement])] = {
    super.expectedTypeEx(fromUnderscore)
  }
}

