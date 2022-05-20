package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.extensions.ClassQualifiedName
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

object Scala3Conversion {
  def unapply(tp: ScType): Option[(ScType, ScType)] = tp match {
    case ParameterizedType(ScDesignatorType(ClassQualifiedName("scala.Conversion")), Seq(argType, resType)) =>
      Some((argType, resType))
    case _ => None
  }
}
