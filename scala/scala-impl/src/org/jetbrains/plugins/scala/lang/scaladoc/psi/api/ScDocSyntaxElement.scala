package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

trait ScDocSyntaxElement extends ScalaPsiElement {
  private var flags: Int = 0
  
  def getFlags: Int = flags
  
  def setFlag(flag: Int): Unit = {
    flags |= flag
  }
  
  def reverseFlag(flag: Int): Unit = {
    flags ^= flag
  }
  
  def clearFlag(flag: Int): Unit = {
    flags &= ~flag
  }
  
  def clearAll(): Unit = {
    flags = 0
  }
}