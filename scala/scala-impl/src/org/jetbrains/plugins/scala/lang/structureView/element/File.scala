package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

// TODO Provide the element dynamically (or, at least, test how all that works in console)
private class File(file: () => ScalaFile) extends AbstractTreeElementDelegatingChildrenToPsi(file()) {
  override def getPresentableText: String = file().name

  override def children: Seq[PsiElement] = file().getChildren.toSeq

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}
