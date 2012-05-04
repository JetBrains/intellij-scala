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
* Date: 08.05.2008
*/

class ScalaValueStructureViewElement(private val element: PsiElement, val isInherited: Boolean) extends ScalaStructureViewElement(element, isInherited) {
  def getPresentation: ItemPresentation = {
    new ScalaValueItemPresentation(element, isInherited);
  }

  def getChildren: Array[TreeElement] = Array.empty
}