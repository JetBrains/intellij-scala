package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScMethodLikeExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor

import scala.collection.Seq

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/
class ScSelfInvocationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSelfInvocation {
  override def toString: String = "SelfInvocation"

  def bind: Option[PsiElement] = bindInternal(shapeResolve = false)

  private def bindInternal(shapeResolve: Boolean): Option[PsiElement] = {
    val seq = bindMultiInternal(shapeResolve)
    if (seq.length == 1) Some(seq(0))
    else None
  }

  private def bindMultiInternal(shapeResolve: Boolean): Seq[PsiElement] = {
    val psiClass = PsiTreeUtil.getContextOfType(this, classOf[PsiClass])
    if (psiClass == null) return Seq.empty
    if (!psiClass.isInstanceOf[ScClass]) return Seq.empty
    val clazz = psiClass.asInstanceOf[ScClass]
    val method = PsiTreeUtil.getContextOfType(this, classOf[ScFunction])
    if (method == null) return Seq.empty
    val expressions: Seq[Expression] = args match {
      case Some(arguments) => arguments.exprs.map(new Expression(_))
      case None => Seq.empty
    }
    val proc = new MethodResolveProcessor(this, "this", List(expressions), Seq.empty,
      Seq.empty /*todo: ? */, StdKinds.methodsOnly, constructorResolve = true, isShapeResolve = shapeResolve,
      enableTupling = true, selfConstructorResolve = true)
    for (constr <- clazz.secondaryConstructors.filter(_ != method) if constr != method) {
      proc.execute(constr, ResolveState.initial)
    }
    clazz.constructor match {
      case Some(constr) => proc.execute(constr, ResolveState.initial())
      case _ =>
    }
    proc.candidates.toSeq.map(_.element)
  }

  private def workWithBindInternal(bindInternal: Option[PsiElement], i: Int): TypeResult[ScType] = {
    val (res: ScType, clazz: ScTemplateDefinition) = bindInternal match {
      case Some(c: ScMethodLike) =>
        val methodType = c.nestedMethodType(i).getOrElse(return Failure("Not enough parameter sections", Some(this)))
        (methodType, c.containingClass)
      case _ => return Failure("Cannot shape resolve self invocation", Some(this))
    }
    clazz match {
      case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
        val params: Seq[TypeParameter] = tp.typeParameters.map(new TypeParameter(_))
        Success(ScTypePolymorphicType(res, params), Some(this))
      case _ => Success(res, Some(this))
    }
  }

  def shapeType(i: Int): TypeResult[ScType] = {
    val option = bindInternal(shapeResolve = true)
    workWithBindInternal(option, i)
  }

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]] = {
    bindMultiInternal(shapeResolve = true).map(pe => workWithBindInternal(Some(pe), i))
  }

  def multiType(i: Int): Seq[TypeResult[ScType]] = {
    bindMultiInternal(shapeResolve = false).map(pe => workWithBindInternal(Some(pe), i))
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitSelfInvocation(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitSelfInvocation(this)
      case _ => super.accept(visitor)
    }
  }
}