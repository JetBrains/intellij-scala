package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

import api.toplevel.typedef._
import com.intellij.psi.{PsiNamedElement, PsiTypeParameterListOwner}
import api.statements.ScTypeAlias

case class ScDesignatorType(val element: PsiNamedElement) extends ScType {
  override def equiv(t: ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
    case p : ScProjectionType => p equiv this
    case _ => false
  }
}

import _root_.scala.collection.immutable.{Map, HashMap}
import com.intellij.psi.PsiTypeParameter

case class ScParameterizedType(designator : ScDesignatorType, typeArgs : Array[ScType]) extends ScType {
  val designated = designator.element
  val substitutor : ScSubstitutor = designated match {
    case owner : PsiTypeParameterListOwner => {
      var map : Map[PsiTypeParameter, ScType] = HashMap.empty
      for (p <- owner.getTypeParameters zip typeArgs) {
        map = map + p
      }
      new ScSubstitutor(map, Map.empty)
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

case class ScTypeAliasDesignatorType(alias : ScTypeAlias, subst : ScSubstitutor) extends ScDesignatorType(alias) {
  override def equiv (t : ScType) = t match {
    case ScTypeAliasDesignatorType(a1, s1) => alias eq a1/* && (subst equiv s1)*/
  }
}