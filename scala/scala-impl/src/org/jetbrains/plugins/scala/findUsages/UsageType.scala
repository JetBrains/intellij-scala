package org.jetbrains.plugins.scala.findUsages

import org.jetbrains.plugins.scala.ScalaBundle

private[scala] sealed trait UsageType {
  override def toString: String = this match {
    case UsageType.SAMInterfaceImplementation => ScalaBundle.message("bytecode.indices.target.sam.type")
    case UsageType.InstanceApplyUnapply => ScalaBundle.message("bytecode.indices.target.unapply.method")
    case UsageType.ForComprehensionMethods => ScalaBundle.message("bytecode.indices.target.for.comprehension.method")
    case UsageType.ImplicitDefinitionUsages => ScalaBundle.message("bytecode.indices.target.implicit.definition")
  }
}

private[scala] object UsageType {
  private[scala] case object SAMInterfaceImplementation extends UsageType
  private[scala] case object ForComprehensionMethods extends UsageType
  private[scala] case object InstanceApplyUnapply extends UsageType
  private[scala] case object ImplicitDefinitionUsages extends UsageType
}
