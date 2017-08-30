package scala.macros.intellij

import com.intellij.openapi.components.{AbstractProjectComponent, ProjectComponent}
import com.intellij.openapi.project.Project

class ScalaMacrosSupportHelper(project: Project) extends AbstractProjectComponent(project) {

}

object ScalaMacrosSupportHelper {
  def getInstance(project: Project): ScalaMacrosSupportHelper = project.getComponent(classOf[ScalaMacrosSupportHelper])
}
