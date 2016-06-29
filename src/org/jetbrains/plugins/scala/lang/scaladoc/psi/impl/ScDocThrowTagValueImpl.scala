package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTagValue

/**
 * User: Dmitry Naydanov
 * Date: 12/9/11
 */

class ScDocThrowTagValueImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) with ScDocTagValue {
  def getValue: String = getText
  
  override def getName: String = getText

  override def isSoft: Boolean = false

  override def getKinds(incomplete: Boolean, completion: Boolean): _root_.org.jetbrains.plugins.scala.lang.resolve.ResolveTargets.ValueSet = StdKinds.stableQualOrClass
}