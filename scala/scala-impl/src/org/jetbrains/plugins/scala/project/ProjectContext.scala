package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdTypes, TypeSystem}

import scala.language.implicitConversions

class ProjectContext(val project: Project) extends AnyVal {
  def stdTypes: StdTypes = StdTypes.instance(this)

  def typeSystem: TypeSystem = ScalaTypeSystem.instance(project)
}

object ProjectContext extends LowerPriority {
  implicit def fromProject(project: Project): ProjectContext = new ProjectContext(project)

  implicit def fromImplicitProject(implicit project: Project): ProjectContext = new ProjectContext(project)

  implicit def toProject(projectContext: ProjectContext): Project = projectContext.project

  implicit def toManager(projectContext: ProjectContext): PsiManager =
    PsiManager.getInstance(projectContext.project)

  implicit def fromManager(manager: PsiManager): ProjectContext = new ProjectContext(manager.getProject)

  implicit def fromPsi(psiElement: PsiElement): ProjectContext = psiElement.getProject
}

trait LowerPriority {
  implicit def fromImplicitModule(implicit module: Module): ProjectContext = module.getProject

  implicit def fromImplicitPsi(implicit psiElement: PsiElement): ProjectContext = psiElement.getProject

  implicit def fromElementScope(elementScope: ElementScope): ProjectContext = elementScope.projectContext

  implicit def fromImplicitElementScope(implicit elementScope: ElementScope): ProjectContext = elementScope.projectContext
}

trait ProjectContextOwner {
  implicit def projectContext: ProjectContext
}