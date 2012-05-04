package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableStructureViewElement(private val element: PsiElement, val isInherited: Boolean) extends ScalaStructureViewElement(element, isInherited) {
  def getPresentation: ItemPresentation = {
    new ScalaVariableItemPresentation(element, isInherited);
  }

  def getChildren: Array[TreeElement] = Array.empty
}