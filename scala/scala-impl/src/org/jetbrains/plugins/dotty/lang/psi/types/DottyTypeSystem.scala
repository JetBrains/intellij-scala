package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
final class DottyTypeSystem private (implicit val projectContext: ProjectContext) extends api.TypeSystem
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

object DottyTypeSystem {
  def instance(implicit projectContext: ProjectContext): DottyTypeSystem = {

    @CachedInUserData(projectContext.project, ProjectRootManager.getInstance(projectContext))
    def cached: DottyTypeSystem = new DottyTypeSystem

    cached
  }
}