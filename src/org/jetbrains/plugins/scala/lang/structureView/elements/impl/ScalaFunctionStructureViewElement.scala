package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import itemsPresentations.impl._
import psi.api.statements.ScFunction

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionStructureViewElement(private val func: ScFunction, val isInherited: Boolean) extends ScalaStructureViewElement(func, isInherited) {

  def getPresentation(): ItemPresentation = {
    return new ScalaFunctionItemPresentation(func, isInherited);
  }

  def getChildren(): Array[TreeElement] = Array()



}