package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.{PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

case class ScDesignatorType(val element: PsiNamedElement) extends ScType {
  override def equiv(t: ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
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
      return designator1.equiv(designator1) &&
             typeArgs.equalsWith(typeArgs1) {_ equiv _}
    }
    case _ => false
  }
}