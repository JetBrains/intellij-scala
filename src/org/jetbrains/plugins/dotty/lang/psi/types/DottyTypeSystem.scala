package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
final class DottyTypeSystem(implicit val projectContext: ProjectContext) extends api.TypeSystem
  with DottyEquivalence
  with DottyConformance
  with DottyBounds
  with DottyPsiTypeBridge
  with DottyTypePresentation {

  override val name = "Dotty"

  override def andType(types: Seq[ScType]) = DottyAndType(types)

  override def parameterizedType(designator: ScType, typeArguments: Seq[ScType]): DottyRefinedType =
    DottyRefinedType(designator)(typeArguments)

}
