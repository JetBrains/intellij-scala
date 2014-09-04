package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElementVisitor, PsiMethod, PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterizedTypeElement {
  override def toString: String = "ParametrizedTypeElement: " + getText

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  def findConstructor = {
    getContext match {
      case constr: ScConstructor => Some(constr)
      case _ => None
    }
  }

  private var desugarizedTypeModCount: Long = 0L
  private var desugarizedType: Option[ScTypeElement] = null

  def desugarizedExistentialType: Option[ScTypeElement] = {
    def inner(): Option[ScTypeElement] = {
      typeArgList.typeArgs.find {
        case e: ScWildcardTypeElementImpl => true
        case _ => false
      } match {
        case Some(_) =>
          val forSomeBuilder = new StringBuilder
          var count = 1
          forSomeBuilder.append(" forSome {")
          val typeElements = typeArgList.typeArgs.map {
            case w: ScWildcardTypeElement =>
              forSomeBuilder.append("type _" + "$" + count +
                w.lowerTypeElement.fold("")(te => s" >: ${te.getText}") +
                w.upperTypeElement.fold("")(te => s" <: ${te.getText}"))
              forSomeBuilder.append("; ")
              val res = s"_$$$count"
              count += 1
              res
            case t => t.getText
          }
          forSomeBuilder.delete(forSomeBuilder.length - 2, forSomeBuilder.length)
          forSomeBuilder.append("}")
          val newTypeText = s"(${typeElement.getText}${typeElements.mkString("[", ", ", "]")} ${forSomeBuilder.toString()})"
          val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, getContext, this)
          Option(newTypeElement)
        case _ => None
      }
    }

    synchronized {
      val currModCount = getManager.getModificationTracker.getModificationCount
      if (desugarizedType != null && desugarizedTypeModCount == currModCount) {
        return desugarizedType
      }
      desugarizedType = inner()
      desugarizedTypeModCount = currModCount
      return desugarizedType
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    desugarizedExistentialType match {
      case Some(typeElement) =>
        return typeElement.getType(TypingContext.empty)
      case _ =>
    }
    val tr = typeElement.getType(ctx)
    val res = tr.getOrElse(return tr)

    //todo: possible refactoring to remove parameterized type inference in simple type
    typeElement match {
      case s: ScSimpleTypeElement =>
        s.reference match {
          case Some(ref) =>
            if (ref.isConstructorReference) {
              ref.resolveNoConstructor match {
                case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
                  if to.isInstanceOf[PsiNamedElement] =>
                  return tr //all things were done in ScSimpleTypeElementImpl.innerType
                case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
                  if to.isInstanceOf[PsiNamedElement] =>
                  return tr //all things were done in ScSimpleTypeElementImpl.innerType
                case _ =>
              }
            }
            ref.bind() match {
              case Some(ScalaResolveResult(e: PsiMethod, _)) =>
                return tr //all things were done in ScSimpleTypeElementImpl.innerType
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    val args: scala.Seq[ScTypeElement] = typeArgList.typeArgs
    if (args.length == 0) return tr
    val argTypesWrapped = args.map {_.getType(ctx)}
    val argTypesgetOrElseped = argTypesWrapped.map {_.getOrAny}
    def fails(t: ScType) = (for (f@Failure(_, _) <- argTypesWrapped) yield f).foldLeft(Success(t, Some(this)))(_.apply(_))

    //Find cyclic type references
    argTypesWrapped.find(_.isCyclic) match {
      case Some(_) => fails(ScParameterizedType(res, Seq(argTypesgetOrElseped.toSeq: _*)))
      case None =>
        val typeArgs = args.map(_.getType(ctx))
        val result = ScParameterizedType(res, typeArgs.map(_.getOrAny))
        (for (f@Failure(_, _) <- typeArgs) yield f).foldLeft(Success(result, Some(this)))(_.apply(_))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitParameterizedTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitParameterizedTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}