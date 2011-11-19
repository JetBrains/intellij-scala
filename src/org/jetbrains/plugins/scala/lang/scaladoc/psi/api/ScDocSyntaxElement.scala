package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import lang.psi.ScalaPsiElement

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

trait ScDocSyntaxElement extends ScalaPsiElement {
  private var flags: Int = 0
  
  def getFlags = flags
  
  def setFlag(flag: Int) {
    flags |= flag
  }
  
  def reverseFlag(flag: Int) {
    flags ^= flag
  }
  
  def clearFlag(flag: Int) {
    flags &= ~flag
  }
  
  def clearAll() {
    flags = 0
  }
}