package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update

object PresentationTypeUpdaters {
  private[this] val objectTypeName       = "_root_.java.lang.Object"
  private[this] val productTypeName      = "_root_.scala.Product"
  private[this] val serializableTypeName = "_root_.scala.Serializable"
  private[this] val anyRefTypeName       = "_root_.scala.AnyRef"
  private[this] val uselessTypeNames     = Set(objectTypeName, productTypeName, serializableTypeName, anyRefTypeName)

  val cleanUp: Update = Update {
    case tpe @ ScCompoundType(components, signatureMap, _) =>
      val withoutUselessComponents = components.filterNot(tp => uselessTypeNames.contains(tp.canonicalText))
      val refinementsAreNecessary = withoutUselessComponents.isEmpty

      val newSignatures = if (refinementsAreNecessary) signatureMap else Map.empty[Signature, ScType]
      val newComponents = if (withoutUselessComponents.isEmpty) components.headOption.toList else withoutUselessComponents

      tpe.copy(newComponents, newSignatures)(tpe.projectContext)
  }
}
