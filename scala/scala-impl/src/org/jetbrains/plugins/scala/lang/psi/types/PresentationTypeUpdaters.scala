package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SimpleUpdate
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

object PresentationTypeUpdaters {

  private val uselessTypeNames = Set(
    CommonQualifiedNames.JavaLangObjectCanonical,
    CommonQualifiedNames.ProductCanonical,
    CommonQualifiedNames.ScalaSerializableCanonical,
    CommonQualifiedNames.JavaIoSerializableCanonical,
    CommonQualifiedNames.AnyRefCanonical
  )

  val cleanUp: SimpleUpdate = SimpleUpdate {
    case tpe @ ScCompoundType(components, signatureMap, _) =>
      val withoutUselessComponents = components.filterNot(tp => uselessTypeNames.contains(tp.canonicalText))
      val refinementsAreNecessary = withoutUselessComponents.isEmpty

      val newSignatures = if (refinementsAreNecessary) signatureMap else Map.empty[TermSignature, ScType]
      val newComponents = if (withoutUselessComponents.isEmpty) components.headOption.toList else withoutUselessComponents

      ScCompoundType(newComponents, newSignatures, tpe.typesMap, tpe.forceRefinement)(using tpe.projectContext)
  }
}
