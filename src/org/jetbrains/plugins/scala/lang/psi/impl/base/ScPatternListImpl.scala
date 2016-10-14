package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScPatternListImpl private(stub: StubElement[ScPatternList], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScPatternList {

  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScPatternListStub) =
    this(stub, ScalaElementTypes.PATTERN_LIST, null)

  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = {
    val stub = getStub
    if (stub != null && simplePatterns) {
      return stub.getChildrenByType(ScalaElementTypes.REFERENCE_PATTERN, JavaArrayFactoryUtil.ScReferencePatternFactory)
    }
    findChildrenByClass[ScPattern](classOf[ScPattern])
  }

  def simplePatterns: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPatternListStub].simplePatterns
    }
    patterns.forall {
      _.isInstanceOf[ScReferencePattern]
    }
  }
}