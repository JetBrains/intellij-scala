package org.jetbrains.plugins.scala.structureView.element

import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.extensions.ObjectExt

// TODO make private (after decoupling Test)
trait AbstractNavigatable extends Navigatable { self: Element =>
  override def navigate(b: Boolean): Unit = navigatable.foreach(_.navigate(b))

  override def canNavigate: Boolean = navigatable.exists(_.canNavigate)

  override def canNavigateToSource: Boolean = navigatable.exists(_.canNavigateToSource)

  private def navigatable = element.asOptionOfUnsafe[Navigatable]
}
