package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

// TODO Provide the element dynamically (or, at least, test how all that works in console)
private class File(fileProvider: () => ScalaFile) extends AbstractTreeElement(fileProvider()) {
  override def getPresentableText: String = fileProvider().name

  override def children: Seq[PsiElement] = fileProvider().getChildren

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}
