package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationsImpl extends ScalaStubBasedElementImpl[ScAnnotations] with ScAnnotations{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScAnnotationsStub) = {this(); setStub(stub); setNode(null)}
  override def toString: String = "AnnotationsList"

  def getAnnotations: Array[ScAnnotation] =
    getStubOrPsiChildren(ScalaElementTypes.ANNOTATION, JavaArrayFactoryUtil.ScAnnotationFactory)
}