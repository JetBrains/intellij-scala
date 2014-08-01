package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import lang.psi.impl.base.ScStableCodeReferenceElementImpl
import api.ScDocTagValue
import resolve.StdKinds

/**
 * User: Dmitry Naydanov
 * Date: 12/9/11
 */

class ScDocThrowTagValueImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocTagValue {
  def getValue: String = getText
  
  override def getName = getText

  override def isSoft: Boolean = false

  override def getKinds(incomplete: Boolean, completion: Boolean) = StdKinds.stableQualOrClass
}