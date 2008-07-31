package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import itemsPresentations.impl.ScalaTypeAliasItemPresentation
import psi.api.statements.ScTypeAlias
import psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
 
class ScalaTypeAliasStructureViewElement(private val element: ScTypeAlias) extends ScalaStructureViewElement(element)  {
  def getPresentation(): ItemPresentation = {
    return new ScalaTypeAliasItemPresentation(element);
  }

  def getChildren(): Array[TreeElement] = {
    return new Array[TreeElement](0)
  }
}