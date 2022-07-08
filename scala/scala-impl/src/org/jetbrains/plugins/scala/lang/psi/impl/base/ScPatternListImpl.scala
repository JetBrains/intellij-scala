package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.{ScBindingPatternFactory, ScReferencePatternFactory}
import org.jetbrains.plugins.scala.lang.TokenSets.BINDING_PATTERNS
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.REFERENCE_PATTERN
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub

class ScPatternListImpl private(stub: ScPatternListStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PATTERN_LIST, node) with ScPatternList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScPatternListStub) = this(stub, null)

  override def toString: String = "ListOfPatterns"

  override def patterns: Seq[ScPattern] = {
    if (simplePatternsByStub) getStubOrPsiChildren(REFERENCE_PATTERN, ScReferencePatternFactory)
    else findChildrenByClass(classOf[ScPattern])
  }.toSeq

  override def bindings: Seq[ScBindingPattern] =
    byStubOrPsi(_.getChildrenByType(BINDING_PATTERNS, ScBindingPatternFactory).toSeq) {
      patterns.flatMap(_.bindings)
    }

  override def simplePatterns: Boolean = simplePatternsByStub || patterns.forall(_.isInstanceOf[ScReferencePattern])

  private def simplePatternsByStub: Boolean = Option(getGreenStub).exists(_.simplePatterns)
}