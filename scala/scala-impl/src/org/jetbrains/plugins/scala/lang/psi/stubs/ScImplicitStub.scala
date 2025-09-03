package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.lang.ir.typeTree.{TypeTree, TypeTreeHolder}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitInstanceIndex}
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.AnyFqn

import scala.annotation.tailrec

trait ScImplicitStub[T <: PsiElement] extends StubElement[T] {

  /**
   * Non-trivial class names of a return type of implicit function or val,
   * or super classes of an implicit object.
   * It is in the same form as written in source or decompiled class file, so it may have prefix.
   */
  def implicitClassNames: Array[String]

  def implicitConversionParameterClass: Option[String] = None

  def indexImplicits(sink: IndexSink): Unit = implicitConversionParameterClass match {
    case Some(paramClass) => ImplicitConversionIndex.occurrence(sink, paramClass)
    case _                => ImplicitInstanceIndex.occurrences(sink, implicitClassNames)
  }
}

object ScImplicitStub {
  def implicitClassNames(psi: ScModifierListOwner, typeElement: => Option[TypeTree]): Array[String] = {
    if (psi.getModifierList.isImplicit)
      typeElement.toArray.flatMap(classNames)
    else EMPTY_STRING_ARRAY
  }

  def superClassNames(obj: ScObject): Array[String] = {
    for {
      templateParent <- obj.extendsBlock.templateParents.toArray
      typeElement    <- templateParent.typeElements
      className      <- classNames(typeElement.typeTree)
    } yield {
      className
    }
  }

  def conversionParamClass(f: ScParameterOwner with ScTypeParametersOwner): Option[String] =
    for {
      param         <- f.parameters.headOption
      paramTypeElem <- param.typeTreeHolder
      className     <- classOrUpperBoundClass(paramTypeElem.typeTree, f)
    } yield {
      className
    }

  private def classOrUpperBoundClass(typeTree: TypeTree, owner: ScTypeParametersOwner): Option[String] = {
    def className(tt: TypeTree) = classNames(tt).headOption

    //it is very common to have implicit conversion defined for a type parameter
    //we cannot do a real resolve during indexing, but this heuristic works good enough
    @tailrec
    def findTypeParam(owner: ScTypeParametersOwner, tpName: String): Option[ScTypeParam] = {
      if (owner == null) None
      else owner.typeParameters.find(_.name == tpName) match {
        case None =>
          val parentOwner = PsiTreeUtil.getParentOfType(owner, classOf[ScTypeParametersOwner], true)
          findTypeParam(parentOwner, tpName)
        case tp => tp
      }
    }

    @tailrec
    def simpleName(te: TypeTree): Option[String] = te match {
      case TypeTree.SimpleType(None, refName) => Some(refName)
      case TypeTree.ParenthesizedType(inner) => simpleName(inner)
      case _ => None
    }

    simpleName(typeTree) match {
      case Some(name) =>
        findTypeParam(owner, name) match {
          case Some(typeParam) =>
            typeParam.upperTypeTreeHolder match {
              case None        => Some(AnyFqn)
              case Some(upper) => className(upper.typeTree)
            }
          case None => className(typeTree)
        }
      case None => className(typeTree)
    }
  }
}