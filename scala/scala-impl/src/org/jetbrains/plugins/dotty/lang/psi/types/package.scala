package org.jetbrains.plugins.dotty.lang.psi

import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Nikolay.Tropin
  * 24-Apr-17
  */
package object types {
  def DottyTypeSystem(implicit pc: ProjectContext) = new DottyTypeSystem()
}
