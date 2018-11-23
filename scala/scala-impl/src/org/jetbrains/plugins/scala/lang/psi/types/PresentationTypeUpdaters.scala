package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

object PresentationTypeUpdaters {

  private[this] val uselessTypeNames = Set(JavaObject, ProductFqn, SerializableFqn, AnyRefFqn)

  val cleanUp: Update = Update {
    case tpe @ ScCompoundType(components, signatureMap, _) =>
      val withoutUselessComponents = components.filterNot(tp => uselessTypeNames.contains(tp.canonicalText))
      val refinementsAreNecessary = withoutUselessComponents.isEmpty

      val newSignatures = if (refinementsAreNecessary) signatureMap else Map.empty[Signature, ScType]
      val newComponents = if (withoutUselessComponents.isEmpty) components.headOption.toList else withoutUselessComponents

      tpe.copy(newComponents, newSignatures)(tpe.projectContext)
  }
}
