package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ScConstructorInvocation extends ScalaPsiElement with ConstructorInvocationLike {
  def typeElement: ScTypeElement

  def simpleTypeElement: Option[ScSimpleTypeElement]

  override def typeArgList: Option[ScTypeArgs]

  def args: Option[ScArgumentExprList]

  override def arguments: Seq[ScArgumentExprList]

  def expectedType: Option[ScType]

  def templateDefinitionContext: Option[ScTemplateDefinition]

  def newTemplate: Option[ScNewTemplateDefinition]

  def shapeType(i: Int): TypeResult

  def shapeMultiType(i: Int): Array[TypeResult]

  def multiType(i: Int): Array[TypeResult]

  def reference: Option[ScStableCodeReference]

  def matchedParameters: Seq[(ScExpression, Parameter)]
}

object ScConstructorInvocation {
  def unapply(c: ScConstructorInvocation): Option[(ScTypeElement, Seq[ScArgumentExprList])] = {
    Option(c).map(it => (it.typeElement, it.arguments))
  }

  object reference {
    def unapply(c: ScConstructorInvocation): Option[ScStableCodeReference] = c.reference
  }

  object byReference {
    def unapply(ref: ScReference): Option[ScConstructorInvocation] = {
      PsiTreeUtil.getParentOfType(ref, classOf[ScConstructorInvocation]) match {
        case null => None
        case c if c.reference.contains(ref) => Some(c)
        case _ => None
      }
    }
  }

  object byUniversalApply {
    def unapply(ref: ScReference): Option[ScMethodCall] = ref.getParent match {
      case call: ScMethodCall if call.isInScala3File =>
        call.target match {
          case Some(srr: ScalaResolveResult) =>
            val realResolveResult = srr.innerResolveResult.getOrElse(srr)
            realResolveResult.element match {
              case Constructor(_) => Some(call)
              case _ => None
            }
          case None => None
        }
      case _ => None
    }
  }
}
