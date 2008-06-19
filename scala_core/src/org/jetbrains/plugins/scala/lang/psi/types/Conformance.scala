package org.jetbrains.plugins.scala.lang.psi.types

import api.statements._
import params._

import com.intellij.psi._

object Conformance {
  def conforms (t1 : ScType, t2 : ScType) : Boolean = {
    if (t1 equiv t2) true
    else t1 match {
      case Any => true
      case Nothing => false
      case Null => t2 == Nothing
      case AnyRef => t2 match {
        case Null => true
        case _: ScParameterizedType => true
        case _: ScDesignatorType => true
        case _: ScSingletonType => true
        case _ => false
      }
      case Singleton => t2 match {
        case _: ScSingletonType => true
        case _ => false
      }
      case AnyVal => t2 match {
        case _: ValType => true
        case _ => false
      }
      case ValType(_, tSuper) => tSuper match {
        case Some(tSuper) => conforms(t1, tSuper)
        case _ => false
      }
      case ScDesignatorType(ta : ScTypeAlias) => conforms(ta.lowerBound, t2)
      case ScDesignatorType(tp : ScTypeParam) => conforms(tp.lowerBound, t2)
      case ScDesignatorType(tp : PsiTypeParameter) => t2 == Nothing //Q: what about AnyRef?
      case _ => t2 match {
        case ScDesignatorType(ta : ScTypeAlias) => conforms(t1, ta.upperBound)
        case ScDesignatorType(tp : ScTypeParam) => conforms(t1, tp.upperBound)
        case _ => false //todo
      }
    }
  }
}