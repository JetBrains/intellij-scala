package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.{PsiMethod, ResolveState, PsiElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */

class ConstructorResolveProcessor(constr: PsiElement, args: List[Seq[Expression]], typeArgs: Seq[ScTypeElement])
        extends MethodResolveProcessor(constr, "this", args, typeArgs) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    element match {
      case method: PsiMethod if method.isConstructor =>
        addResult(new ScalaResolveResult(method, getSubst(state)))
      case _ =>
    }
    return true
  }
}