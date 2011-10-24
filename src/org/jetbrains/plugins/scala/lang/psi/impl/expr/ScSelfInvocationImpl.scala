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
import api.toplevel.typedef.{ScTemplateDefinition, ScClass}
import api.toplevel.ScTypeParametersOwner
import types.result.{Success, Failure, TypeResult}
import types.nonvalue.{ScTypePolymorphicType, TypeParameter}
import types.{Any, Nothing, ScType}
import api.base.{ScMethodLike, ScPrimaryConstructor}
import collection.Seq

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSelfInvocationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSelfInvocation {
  override def toString: String = "SelfInvocation"


  def bind: Option[PsiElement] = bindInternal(false)

  private def bindInternal(shapeResolve: Boolean): Option[PsiElement] = {
    val seq = bindMultiInternal(shapeResolve)
    if (seq.length == 1) Some(seq(0))
    else None
  }

  private def bindMultiInternal(shapeResolve: Boolean): Seq[PsiElement] = {
    val psiClass = PsiTreeUtil.getParentOfType(this, classOf[PsiClass])
    if (psiClass == null) return Seq.empty
    if (!psiClass.isInstanceOf[ScClass]) return Seq.empty
    val clazz = psiClass.asInstanceOf[ScClass]
    val method = PsiTreeUtil.getParentOfType(this, classOf[ScFunction])
    if (method == null) return Seq.empty
    val constructors: Array[PsiMethod] = clazz.getConstructors.filter(_ != method)
    if (args == None) return Seq.empty
    val arguments = args.get
    val proc = new MethodResolveProcessor(this, "this", List(arguments.exprs.map(new Expression(_))), Seq.empty,
      Seq.empty /*todo: ? */, StdKinds.methodsOnly, constructorResolve = true, isShapeResolve = shapeResolve, enableTupling = true)
    for (constr <- constructors) {
      proc.execute(constr, ResolveState.initial)
    }
    proc.candidates.toSeq.map(_.element)
  }

  private def workWithBindInternal(bindInternal: Option[PsiElement], i: Int): TypeResult[ScType] = {
    val (res: ScType, clazz: ScTemplateDefinition) = bindInternal match {
      case Some(c: ScMethodLike) =>
        val methodType = ScType.nested(c.methodType, i).getOrElse(return Failure("Not enough parameter sections", Some(this)))
        (methodType, c.getContainingClass)
      case _ => return Failure("Cannot shape resolve self invocation", Some(this))
    }
    clazz match {
      case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 =>
        val params: Seq[TypeParameter] = tp.typeParameters.map(tp =>
          new TypeParameter(tp.name,
            tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        Success(ScTypePolymorphicType(res, params), Some(this))
      case _ => Success(res, Some(this))
    }
  }

  def shapeType(i: Int): TypeResult[ScType] = {
    val option = bindInternal(true)
    workWithBindInternal(option, i)
  }

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]] = {
    bindMultiInternal(true).map(pe => workWithBindInternal(Some(pe), i))
  }

  def multiType(i: Int): Seq[TypeResult[ScType]] = {
    bindMultiInternal(false).map(pe => workWithBindInternal(Some(pe), i))
  }
}