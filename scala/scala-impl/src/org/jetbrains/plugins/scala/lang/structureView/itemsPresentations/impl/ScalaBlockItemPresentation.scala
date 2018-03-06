package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

class ScalaBlockItemPresentation(definition: ScBlock) extends ScalaItemPresentation(definition) {
  override def getPresentableText: String = ""

  override def getIcon(open: Boolean): Icon = PlatformIcons.CLASS_INITIALIZER
}