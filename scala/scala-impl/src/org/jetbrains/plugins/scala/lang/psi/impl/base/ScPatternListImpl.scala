package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScReferencePatternFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.REFERENCE_PATTERN
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScPatternListImpl private(stub: ScPatternListStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.PATTERN_LIST, node) with ScPatternList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScPatternListStub) = this(stub, null)

  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = {
    if (simplePatternsByStub) getStubOrPsiChildren(REFERENCE_PATTERN, ScReferencePatternFactory)
    else findChildrenByClass(classOf[ScPattern])
  }

  def simplePatterns: Boolean = simplePatternsByStub || patterns.forall(_.isInstanceOf[ScReferencePattern])

  private def simplePatternsByStub: Boolean = Option(getGreenStub).exists(_.simplePatterns)
}