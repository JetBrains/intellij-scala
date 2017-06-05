package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
final class ScalaTypeSystem(implicit val projectContext: ProjectContext) extends api.TypeSystem
  with ScalaEquivalence
  with ScalaConformance
  with ScalaBounds
  with ScalaPsiTypeBridge
  with ScalaTypePresentation {

  override val name = "Scala"

  override def andType(types: Seq[ScType]) = ScCompoundType(types)

  override def parameterizedType(designator: ScType, typeArguments: Seq[ScType]) =
    ScParameterizedType(designator, typeArguments)
}
