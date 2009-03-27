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
 
class ScalaTypeAliasStructureViewElement(private val element: ScTypeAlias, val isInherited: Boolean) extends ScalaStructureViewElement(element, isInherited)  {
  def getPresentation(): ItemPresentation = new ScalaTypeAliasItemPresentation(element, isInherited)

  def getChildren(): Array[TreeElement] = new Array[TreeElement](0)}