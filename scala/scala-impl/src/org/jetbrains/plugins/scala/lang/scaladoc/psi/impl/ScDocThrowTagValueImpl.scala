package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTagValue

/**
 * User: Dmitry Naydanov
 * Date: 12/9/11
 */
final class ScDocThrowTagValueImpl(node: ASTNode) extends ScStableCodeReferenceImpl(node) with ScDocTagValue {

  override def getName: String = getText

  override def isSoft: Boolean = false

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet = StdKinds.stableQualOrClass
}