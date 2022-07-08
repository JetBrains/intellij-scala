package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScalaTypeSystem private (implicit override val projectContext: ProjectContext) extends api.TypeSystem
  with ScalaEquivalence
  with ScalaConformance
  with ScalaBounds
  with ScalaPsiTypeBridge
  with ScalaTypePresentation {

  override val name = "Scala"

  override def andType(types: Seq[ScType]): ScType = ScCompoundType(types)

  override def parameterizedType(designator: ScType, typeArguments: Seq[ScType]): ValueType =
    ScParameterizedType(designator, typeArguments.toSeq)
}

object ScalaTypeSystem {
  def instance(implicit projectContext: ProjectContext): ScalaTypeSystem = {

    @CachedInUserData(projectContext.project, ProjectRootManager.getInstance(projectContext))
    def cached: ScalaTypeSystem = new ScalaTypeSystem

    cached
  }
}
