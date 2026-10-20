package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.ScalaInheritorsFinder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

object SealedClassInheritors {
  def unapply(cls: ScTypeDefinition): Option[Seq[ScTypeDefinition]] = Option.when(cls.isSealed) {
    ScalaInheritorsFinder.instance.inheritorsInFile(cls)
      .toSeq
      .filterByType[ScTypeDefinition]
  }
}
