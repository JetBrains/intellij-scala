package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.util.treeView.smartTree.{NodeProvider, TreeElement}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

@ApiStatus.Internal
trait ScalaStructureViewModelProvider {
  def nodeProvidersFor(rootElement: ScalaFile): Seq[NodeProvider[_ <: TreeElement]]
}

object ScalaStructureViewModelProvider extends ExtensionPointDeclaration[ScalaStructureViewModelProvider](
  "org.intellij.scala.structureViewModelProvider") {

  def nodeProvidersFor(rootElement: ScalaFile): Seq[NodeProvider[_ <: TreeElement]] =
    implementations.flatMap(_.nodeProvidersFor(rootElement))
}
