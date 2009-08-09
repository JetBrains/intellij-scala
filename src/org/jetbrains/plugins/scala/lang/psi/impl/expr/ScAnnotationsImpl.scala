package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.util.ArrayFactory
import parser.ScalaElementTypes
import psi.stubs.ScAnnotationsStub
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationsImpl extends ScalaStubBasedElementImpl[ScAnnotations] with ScAnnotations{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScAnnotationsStub) = {this(); setStub(stub); setNode(null)}
  override def toString: String = "AnnotationsList"


  def getAnnotations: Array[ScAnnotation] = {
    getStubOrPsiChildren(ScalaElementTypes.ANNOTATION, new ArrayFactory[ScAnnotation] {
      def create(count: Int): Array[ScAnnotation] = new Array[ScAnnotation](count)
    })
  }
}