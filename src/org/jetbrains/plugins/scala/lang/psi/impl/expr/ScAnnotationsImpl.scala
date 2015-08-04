package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationsImpl private (stub: StubElement[ScAnnotations], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScAnnotations{
  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScAnnotationsStub) = {this(stub, ScalaElementTypes.ANNOTATIONS, null)}
  override def toString: String = "AnnotationsList"

  def getAnnotations: Array[ScAnnotation] =
    getStubOrPsiChildren(ScalaElementTypes.ANNOTATION, JavaArrayFactoryUtil.ScAnnotationFactory)
}