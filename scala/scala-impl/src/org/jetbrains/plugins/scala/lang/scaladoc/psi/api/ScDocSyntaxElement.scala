package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocSyntaxElement extends ScalaPsiElement {
  private var flags: Int = 0
  
  def getFlags: Int = flags

  def setFlag(flag: Int): Unit = flags |= flag
}