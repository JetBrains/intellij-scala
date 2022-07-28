package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ConstructorInvocationLikeImpl
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState, StdKinds}

class ScSelfInvocationImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScSelfInvocation with ConstructorInvocationLikeImpl {

  override def args: Option[ScArgumentExprList] =
    findChild[ScArgumentExprList]

  override def arguments: Seq[ScArgumentExprList] =
    findChildren[ScArgumentExprList]

  override def typeArgList: Option[ScTypeArgs] = None

  override def bind: Option[PsiElement] = bindInternal(shapeResolve = false)

  //@Cached(BlockModificationTracker(this), this)
  override def multiResolve: Seq[ScalaResolveResult] = {
    multiResolveInternal(shapeResolve = false).toSeq
  }

  private def bindInternal(shapeResolve: Boolean): Option[PsiElement] =
    bindMultiInternal(shapeResolve) match {
      case Array(head) => Some(head)
      case _ => None
    }

  private def multiResolveInternal(shapeResolve: Boolean): Array[ScalaResolveResult] = {
    val consOwner = PsiTreeUtil.getContextOfType(this, classOf[ScClass], classOf[ScEnum], classOf[ScTrait])
    if (consOwner == null) return Array.empty

    val method = PsiTreeUtil.getContextOfType(this, classOf[ScFunction])
    if (method == null) return Array.empty

    val expressions: Seq[Expression] = args match {
      case Some(arguments) => arguments.exprs
      case None            => Seq.empty
    }

    val proc = new MethodResolveProcessor(
      this,
      "this",
      List(expressions),
      Seq.empty,
      Seq.empty /*todo: ? */,
      StdKinds.methodsOnly,
      constructorResolve     = true,
      isShapeResolve         = shapeResolve,
      enableTupling          = true,
      selfConstructorResolve = true
    )

    for (constr <- consOwner.secondaryConstructors.filter(_ != method)) {
      proc.execute(constr, ScalaResolveState.empty)
    }

    consOwner.constructor match {
      case Some(constr) => proc.execute(constr, ScalaResolveState.empty)
      case _            =>
    }

    proc.candidates
  }

  private def bindMultiInternal(shapeResolve: Boolean): Array[PsiElement] = {
    multiResolveInternal(shapeResolve).map(_.element)
  }

  private def workWithBindInternal(bindInternal: Option[PsiElement], i: Int): TypeResult = {
    val (res: ScType, clazz: ScTemplateDefinition) = bindInternal match {
      case Some(c: ScMethodLike) =>
        val methodType = c.nestedMethodType(i).getOrElse(return Failure(ScalaBundle.message("not.enough.parameter.sections")))
        (methodType, c.containingClass)
      case _ => return Failure(ScalaBundle.message("cannot.shape.resolve.self.invocation"))
    }
    clazz match {
      case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
        val params: Seq[TypeParameter] = tp.typeParameters.map(TypeParameter(_))
        Right(ScTypePolymorphicType(res, params))
      case _ => Right(res)
    }
  }

  override def shapeType(i: Int): TypeResult = {
    val option = bindInternal(shapeResolve = true)
    workWithBindInternal(option, i)
  }

  override def shapeMultiType(i: Int): Array[TypeResult] = {
    bindMultiInternal(shapeResolve = true).map(pe => workWithBindInternal(Some(pe), i))
  }

  override def multiType(i: Int): Array[TypeResult] = {
    bindMultiInternal(shapeResolve = false).map(pe => workWithBindInternal(Some(pe), i))
  }

  override def toString: String = "SelfInvocation"

  override def handleElementRename(newElementName: String): PsiElement = this

  override def getVariants: Array[AnyRef] = bind.toArray

  override def isReferenceTo(element: PsiElement): Boolean = bind.contains(element)

  override def bindToElement(element: PsiElement): PsiElement = this

  override def getCanonicalText: String = "this"

  override def getElement: PsiElement = this

  override def resolve(): PsiElement = bind.orNull

  override def isSoft: Boolean = false

  override def getRangeInElement: TextRange = {
    val start = this.getTextRange.getStartOffset
    Option(thisElement).getOrElse(this).getTextRange.shiftRight(-start)
  }

  override def resolveConstructor(): PsiElement = resolve()
}