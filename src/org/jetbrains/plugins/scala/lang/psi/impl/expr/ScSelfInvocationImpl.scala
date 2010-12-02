package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.resolve.processor.MethodResolveProcessor
import api.statements.ScFunction
import util.PsiTreeUtil
import types.Compatibility.Expression
import lang.resolve.StdKinds
import api.base.ScPrimaryConstructor
import api.toplevel.typedef.{ScTemplateDefinition, ScClass}
import api.toplevel.ScTypeParametersOwner
import types.result.{Success, Failure, TypeResult}
import types.nonvalue.{ScTypePolymorphicType, TypeParameter}
import types.{Any, Nothing, ScType}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSelfInvocationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSelfInvocation {
  override def toString: String = "SelfInvocation"


  def bind: Option[PsiElement] = bindInternal(false)

  def bindInternal(shapeResolve: Boolean): Option[PsiElement] = {
    val psiClass = PsiTreeUtil.getParentOfType(this, classOf[PsiClass])
    if (psiClass == null) return None
    if (!psiClass.isInstanceOf[ScClass]) return None
    val clazz = psiClass.asInstanceOf[ScClass]
    val method = PsiTreeUtil.getParentOfType(this, classOf[ScFunction])
    if (method == null) return None
    val constructors: Array[PsiMethod] = clazz.getConstructors.filter(_ != method)
    if (args == None) return None
    val arguments = args.get
    val proc = new MethodResolveProcessor(this, "this", List(arguments.exprs.map(new Expression(_))), Seq.empty,
      StdKinds.methodsOnly, constructorResolve = true, isShapeResolve = shapeResolve, enableTupling = true)
    for (constr <- constructors) {
      proc.execute(constr, ResolveState.initial)
    }
    val candidates = proc.candidates
    if (candidates.length == 1) {
      return Some(candidates(0).element)
    } else return None
  }

  def shapeType(i: Int): TypeResult[ScType] = {
    val (res: ScType, clazz: ScTemplateDefinition) = bindInternal(true) match {
      case Some(c: ScFunction) => (c.methodType, c.getContainingClass)
      case Some(c: ScPrimaryConstructor) => (c.methodType, c.getContainingClass)
      case _ => return Failure("Cannot shape resolve self invocation", Some(this))
    }
    clazz match {
      case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 =>
        val params: Seq[TypeParameter] = tp.typeParameters.map(tp =>
                    new TypeParameter(tp.name,
                      tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp))
        return Success(ScTypePolymorphicType(res, params), Some(this))
      case _ => return Success(res, Some(this))
    }
  }
}