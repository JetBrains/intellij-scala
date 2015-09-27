package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.SimplePropertyGetterProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScReferenceExpression, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author Nikolay.Tropin
 */
class ScalaSimpleGetterProvider extends SimplePropertyGetterProvider {
  override def isInsideSimpleGetter(element: PsiElement): Boolean = {
    PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition]) match {
      case fun: ScFunction if fun.name == "unapply" => false
      case ScFunctionDefinition.withBody(ScBlock(e: ScExpression)) => isSimpleEnough(e)
      case ScFunctionDefinition.withBody(b: ScBlock) => false
      case ScFunctionDefinition.withBody(e: ScExpression) => isSimpleEnough(e)
      case _ => false
    }
  }

  private def isSimpleEnough(e: ScExpression): Boolean = e match {
    case ref: ScReferenceExpression =>
      ref.qualifier.forall(isSimpleEnough) && isGettable(ref.resolve())
    case th: ScThisReference => true
    case _ => false
  }

  private def isGettable(resolve: PsiElement) = resolve match {
    case null => false
    case m: PsiMethod => false
    case o: ScObject => true
    case c: PsiClass => false
    case _ => true
  }
}
