package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdTypes, TypeSystem}

import scala.language.implicitConversions

trait ProjectContext {
  def project: Project

  def stdTypes: StdTypes = StdTypes.instance(this)

  def typeSystem: TypeSystem = ScalaTypeSystem.instance(project)
}

object ProjectContext extends LowerPriority {
  def apply(project: Project): ProjectContext = new SimpleProjectContext(project)

  implicit def fromProject(project: Project): ProjectContext = new SimpleProjectContext(project)

  implicit def fromImplicitProject(implicit project: Project): ProjectContext = new SimpleProjectContext(project)

  implicit def toProject(projectContext: ProjectContext): Project = projectContext.project

  implicit def toManager(projectContext: ProjectContext): PsiManager =
    PsiManager.getInstance(projectContext.project)

  implicit def fromManager(manager: PsiManager): ProjectContext = new SimpleProjectContext(manager.getProject)

  implicit def fromPsi(psiElement: PsiElement): ProjectContext = new SimpleProjectContext(psiElement.getProject)

  private class SimpleProjectContext(override val project: Project) extends ProjectContext
}

trait LowerPriority {
  implicit def fromImplicitModule(implicit module: Module): ProjectContext = ProjectContext(module.getProject)

  implicit def fromImplicitPsi(implicit psiElement: PsiElement): ProjectContext = ProjectContext(psiElement.getProject)
}

trait ProjectContextOwner {
  implicit def projectContext: ProjectContext
}
