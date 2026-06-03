package scala.meta.intellij

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

import scala.meta.trees.TreeConverter

class IDEAContext(project: => Project) extends TreeConverter {

  override def getCurrentProject: Project = project

  // annotations filtering isn't required in converter tests
  override protected val annotationToSkip: ScAnnotation = null
}
