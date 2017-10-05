package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl.ScalaTypeAliasItemPresentation

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
 
class ScalaTypeAliasStructureViewElement(ta: ScTypeAlias, val isInherited: Boolean) extends ScalaStructureViewElement(ta, isInherited)  {
  def getPresentation: ItemPresentation = new ScalaTypeAliasItemPresentation(ta, isInherited)

  def getChildren: Array[TreeElement] = new Array[TreeElement](0)}