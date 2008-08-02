package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

import resolve.{ResolveProcessor, StdKinds}
import api.toplevel.ScPolymorphicElement
import api.toplevel.typedef._
import api.statements.params.ScTypeParam
import com.intellij.psi.{PsiNamedElement, PsiTypeParameterListOwner}
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
  def designated = designator match {
    case des : ScDesignatorType => des.element
    case ScProjectionType(sin@ScSingletonType(path), name) => {
      val proc = new ResolveProcessor(StdKinds.stableClass, name)
      proc.processType(sin, path)
      if (proc.candidates.size == 1) proc.candidates.toArray(0).element else null
    }
  }

  val substitutor : ScSubstitutor = designated match {
    case owner : PsiTypeParameterListOwner => {
      var map : Map[ScTypeVariable, ScType] = HashMap.empty
      for (p <- owner.getTypeParameters zip typeArgs) {
        map = map + ((ScalaPsiManager.typeVariable(p._1), p._2))
      }
      new ScSubstitutor(map, Map.empty, Map.empty)
    }
    case _ => ScSubstitutor.empty
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

case class ScPolymorphicType(poly : ScPolymorphicElement, subst : ScSubstitutor) extends ScDesignatorType(poly) {
  override def equiv (t : ScType) = t match {
    case ScPolymorphicType(p1, s1) => poly eq p1
    case _ => false
  }
}

case class ScTypeVariable(inner : Seq[ScTypeVariable], lower : ScType, upper : ScType) extends ScType

class ScTypeConstructor(args : Seq[ScTypeVariable], t : ScType)