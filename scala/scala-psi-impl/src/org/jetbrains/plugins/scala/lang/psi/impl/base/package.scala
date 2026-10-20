package org.jetbrains.plugins.scala.lang.psi.impl

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

package object base {

  private[base] final def cachedClass(fqn: String)
                                     (implicit projectContext: ProjectContext): ScType =
    ElementScope(projectContext.project).getCachedClass(fqn)
      .fold(api.Nothing: ScType)(ScalaType.designator)
}
