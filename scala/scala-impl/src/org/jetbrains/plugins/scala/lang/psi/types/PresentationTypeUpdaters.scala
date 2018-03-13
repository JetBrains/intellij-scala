package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update

object PresentationTypeUpdaters {
  private[this] val objectTypeName       = "_root_.java.lang.Object"
  private[this] val productTypeName      = "_root_.scala.Product"
  private[this] val serializableTypeName = "_root_.scala.Serializable"
  private[this] val anyRefTypeName       = "_root_.scala.AnyRef"
  private[this] val uselessTypeNames     = Set(objectTypeName, productTypeName, serializableTypeName, anyRefTypeName)

  val removeUnnecessaryRefinements: Update = Update {
    case tpe @ ScCompoundType(Seq(obj), _, _) if obj.canonicalText == objectTypeName => tpe
    case tpe: ScCompoundType =>
      tpe.copy(signatureMap = Map.empty)(tpe.projectContext)
  }

  val removeUselessComponents: Update = Update {
    case tpe @ ScCompoundType(components, _, _) =>
      val filtered = components.filterNot(tpe => uselessTypeNames.contains(tpe.canonicalText))
      tpe.copy(components = filtered)(tpe.projectContext)
  }

  val cleanUp: Seq[Update] = Seq(removeUnnecessaryRefinements, removeUselessComponents)
}
