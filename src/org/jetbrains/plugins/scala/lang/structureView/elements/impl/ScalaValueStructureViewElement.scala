package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

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
* Date: 08.05.2008
*/

class ScalaValueStructureViewElement(private val element: PsiElement, val isInherited: Boolean) extends ScalaStructureViewElement(element, isInherited) {
  def getPresentation(): ItemPresentation = {
    return new ScalaValueItemPresentation(element, isInherited);
  }

  def getChildren(): Array[TreeElement] = {
    return new Array[TreeElement](0)
  }
}