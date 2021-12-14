package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectContext, ProjectContextOwner, ProjectPsiElementExt}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement

trait CallContext extends Any with ProjectContextOwner {
  def isScala3: Boolean
}

final case class ModuleContext(private val module: Module) extends AnyVal with CallContext {
  override def isScala3: Boolean                       = module.hasScala3
  override implicit def projectContext: ProjectContext = module.getProject
}

object CallContext {
  implicit def fromImplicitPsiElement(implicit e: PsiElement): CallContext =
    fromPsiElement(e)

  implicit def fromPsiElement(element: PsiElement): CallContext =
    element.module.fold(DefaultScala2Context()(element): CallContext)(ModuleContext.apply)
}

final case class DefaultScala2Context()(override implicit val projectContext: ProjectContext)
  extends CallContext {
  override def isScala3: Boolean = false
}

final case class Scala3Context()(override implicit val projectContext: ProjectContext)
  extends CallContext {
  override def isScala3: Boolean = true
}

