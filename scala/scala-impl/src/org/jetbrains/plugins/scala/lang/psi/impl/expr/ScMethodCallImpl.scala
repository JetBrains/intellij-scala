package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScReferenceableInfixTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScMethodCallImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScMethodCall {

  def getInvokedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def argumentExpressions: Seq[ScExpression] = if (args != null) args.exprs else Nil

  override def getEffectiveInvokedExpr: ScExpression = {
    findChildByClassScala(classOf[ScExpression]) match {
      case x: ScParenthesisedExpr => x.expr.getOrElse(x)
      case x => x
    }
  }

  override protected def innerType: TypeResult = {
    val kindProjectorEnabled = ScalaPsiUtil.kindProjectorPluginEnabled(this)

    def kindProjectorPolymorphicLambdaType(arg: ScTypeElement,
                                           l: ScSimpleTypeElement,
                                           r: ScSimpleTypeElement,
                                           method: String): TypeResult = {
      val text = s"((${arg.getText}) with Object { def $method[T](x: ${l.getText}[T]): ${r.getText}[T] })"

      createTypeElementFromText(text, getContext, this).`type`()
    }

    def parseKindProjectorGenericCall(g: ScGenericCall):
        Option[(ScTypeElement, ScSimpleTypeElement, ScSimpleTypeElement)] = {

      g.referencedExpr match {
        case elem: ScReferenceExpression =>
          elem.getText match {
            case "Lambda" | "λ" if kindProjectorEnabled =>
              g.arguments match {
                case Seq(infix: ScReferenceableInfixTypeElement) =>
                  (infix.leftTypeElement, infix.rightTypeElement) match {
                    case (l: ScSimpleTypeElement, Some(r: ScSimpleTypeElement)) =>
                      Some((infix, l, r))
                    case _ => None
                  }
                case Seq(
                  tpe @ ScParameterizedTypeElement(
                    _: ScSimpleTypeElement,
                    Seq(l: ScSimpleTypeElement, r: ScSimpleTypeElement)
                  )
                ) => Some((tpe, l, r))
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    }

    getInvokedExpr match {
      case g: ScGenericCall =>
        parseKindProjectorGenericCall(g) match {
          case Some((all, l, r)) => kindProjectorPolymorphicLambdaType(all, l, r, "apply")
          case None => super.innerType
        }
      case ref: ScReferenceExpression =>
        ref.qualifier match {
          case Some(qual) =>
            qual match {
              case g: ScGenericCall =>
                parseKindProjectorGenericCall(g) match {
                  case Some ((all, l, r) ) =>
                    kindProjectorPolymorphicLambdaType(all, l, r, ref.nameId.getText)
                  case None =>
                    super.innerType
                }
              case _ => super.innerType
            }
          case None => super.innerType
        }
      case _ => super.innerType
    }
  }

  override def toString: String = "MethodCall"
}