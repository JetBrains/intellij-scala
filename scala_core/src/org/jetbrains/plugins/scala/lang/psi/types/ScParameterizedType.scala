package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

import api.toplevel.ScTypeParametersOwner
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiTypeParameterListOwner, JavaPsiFacade, PsiElement, PsiNamedElement}
import resolve.{ResolveProcessor, StdKinds}
import api.statements.ScTypeAlias
import api.toplevel.typedef._
import api.statements.params.ScTypeParam
import psi.impl.ScalaPsiManager

case class ScDesignatorType(val element: PsiNamedElement) extends ScType {
  override def equiv(t: ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
    case p : ScProjectionType => p equiv this
    case _ => false
  }
}

import _root_.scala.collection.immutable.{Map, HashMap}
import com.intellij.psi.{PsiTypeParameter, PsiClass}

case class ScParameterizedType(designator : ScType, typeArgs : Array[ScType]) extends ScType {
  def designated = ScType.extractDesignated(designator) match {
    case Some((e, _)) => Some(e)
    case _ => None
  }
  
  val substitutor : ScSubstitutor = {
    val (params, initial) = designator match {
      case ScTypeVariable(_, args, _, _) => (args, ScSubstitutor.empty)
      case ScTypeAliasType(_, args, _, _) => (args, ScSubstitutor.empty)
      case _ => ScType.extractDesignated(designator) match {
        case Some((owner : ScTypeParametersOwner, s)) => (owner.typeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}, s)
        case Some((owner : PsiTypeParameterListOwner, s)) => (owner.getTypeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}, s)
        case _ => (Seq.empty, ScSubstitutor.empty)
      }
    }

    params match {
      case Seq.empty => initial
      case _ => {
        var res = initial
        for (p <- params.toArray zip typeArgs) {
          res = res + (p._1, p._2)
        }
        res
      }
    }
  }

  override def equiv(t: ScType): Boolean = t match {
    case ScParameterizedType(designator1, typeArgs1) => {
      return designator.equiv(designator1) &&
             typeArgs.equalsWith(typeArgs1) {_ equiv _}
    }
    case _ => false
  }
}

object ScParameterizedType {
  def create(c: PsiClass, s : ScSubstitutor) =
    new ScParameterizedType(new ScDesignatorType(c), c.getTypeParameters.map {
      tp => s subst(ScalaPsiManager.typeVariable(tp))
    })
}

case class ScTypeAliasType(name : String, args : List[ScTypeVariable], lower : ScType, upper : ScType) extends ScType {
  override def equiv(t: ScType): Boolean = t match {
    case ScTypeAliasType(n1, args1, l1, u1) => {
      name == n1 && args.length == args1.length && {
        val s = args.zip(args1).foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._2, p._1)}
        lower.equiv(s.subst(l1)) && upper.equiv(s.subst(u1))
      }
    }
    case _ => false
  }
}

case class ScTypeVariable(name : String, inner : List[ScTypeVariable], lower : ScType, upper : ScType) extends ScType

case class ScTypeParameterType(val typeParam : PsiTypeParameter,
                               override val inner : List[ScTypeParameterType],
                               override val lower : ScType,
                               override val upper : ScType)
extends ScTypeVariable(typeParam.getName, inner, lower, upper)
