package org.jetbrains.plugins.scala.lang.psi.types

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
      case _ => false //todo
    }
  }
}