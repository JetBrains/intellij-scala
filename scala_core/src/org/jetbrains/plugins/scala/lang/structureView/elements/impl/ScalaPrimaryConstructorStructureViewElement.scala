package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 16.05.2008
*/

class ScalaPrimaryConstructorStructureViewElement(private val element: ScPrimaryConstructor) extends ScalaStructureViewElement(element, false) {

  def getPresentation(): ItemPresentation = {
    return new ScalaPrimaryConstructorItemPresentation(element);
  }

  def getChildren(): Array[TreeElement] = {
    return new Array[TreeElement](0)
  }
}