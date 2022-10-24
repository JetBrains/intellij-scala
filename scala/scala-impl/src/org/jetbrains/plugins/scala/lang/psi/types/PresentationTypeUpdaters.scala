package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SimpleUpdate
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

object PresentationTypeUpdaters {

  private[this] val uselessTypeNames = Set(JavaObjectCanonical, ProductCanonical, SerializableCanonical, AnyRefCanonical)

  val cleanUp: SimpleUpdate = SimpleUpdate {
    case tpe @ ScCompoundType(components, signatureMap, _) =>
      val withoutUselessComponents = components.filterNot(tp => uselessTypeNames.contains(tp.canonicalText))
      val refinementsAreNecessary = withoutUselessComponents.isEmpty

      val newSignatures = if (refinementsAreNecessary) signatureMap else Map.empty[TermSignature, ScType]
      val newComponents = if (withoutUselessComponents.isEmpty) components.headOption.toList else withoutUselessComponents

      tpe.copy(newComponents, newSignatures)(tpe.projectContext)
  }
}
