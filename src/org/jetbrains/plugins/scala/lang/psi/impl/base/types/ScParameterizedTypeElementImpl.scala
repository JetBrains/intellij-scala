package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types._
import collection.mutable.HashMap
import result.{TypeResult, Success, Failure, TypingContext}
import api.base.ScConstructor
import resolve.ScalaResolveResult
import com.intellij.psi.PsiMethod

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterizedTypeElement {
  override def toString: String = "ParametrizedTypeElement"

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  def findConstructor = {
    getContext match {
      case constr: ScConstructor => Some(constr)
      case _ => None
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val tr = typeElement.getType(ctx)
    val res = tr.getOrElse(return tr)

    typeElement match {
      case s: ScSimpleTypeElement => {
        s.reference match {
          case Some(ref) => {
            ref.bind match {
              case Some(ScalaResolveResult(e: PsiMethod, _)) => return tr //all things were done in ScSimpleTypeElementImpl.innerType
              case _ =>
            }
          }
          case _ =>
        }
      }
      case _ =>
    }

    val argTypesWrapped = typeArgList.typeArgs.map {_.getType(ctx)}
    val argTypesgetOrElseped = argTypesWrapped.map {_.getOrElse(Any)}
    def fails(t: ScType) = (for (f@Failure(_, _) <- argTypesWrapped) yield f).foldLeft(Success(t, Some(this)))(_.apply(_))
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    //Find cyclic type references
    argTypesWrapped.find(_.isCyclic) match {
      case Some(_) => fails(new ScParameterizedType(res, Seq(argTypesgetOrElseped.toSeq: _*)))
      case None => res match {
        case tp: ScTypeConstructorType => {
          val map = new HashMap[(String, String), ScType]
          for (i <- 0 until argTypesgetOrElseped.length.min(tp.args.size)) {
            map += Tuple((tp.args.apply(i).name, tp.args.apply(i).getId), argTypesgetOrElseped(i))
          }
          val subst = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
          fails(subst.subst(tp.aliased.v))
        }
        case _ => {
          val typeArgs = typeArgList.typeArgs.map(_.getType(ctx))
          val result = new ScParameterizedType(res, typeArgs.map(_.getOrElse(Any)))
          (for (f@Failure(_, _) <- typeArgs) yield f).foldLeft(Success(result, Some(this)))(_.apply(_))
        }
      }
    }
  }
}