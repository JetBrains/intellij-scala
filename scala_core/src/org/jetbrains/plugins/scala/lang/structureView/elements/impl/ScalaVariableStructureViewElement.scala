package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableStructureViewElement(private val element: PsiElement, val isInherited: Boolean) extends ScalaStructureViewElement(element) {
  def getPresentation(): ItemPresentation = {
    return new ScalaVariableItemPresentation(element, isInherited);
  }

  def getChildren(): Array[TreeElement] = {
    return new Array[TreeElement](0)
  }
}