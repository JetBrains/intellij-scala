package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub

class ScAnnotationsImpl private (stub: ScAnnotationsStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.ANNOTATIONS, node) with ScAnnotations {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScAnnotationsStub) = this(stub, null)

  override def toString: String = "AnnotationsList"

  override def getAnnotations: Array[ScAnnotation] =
    getStubOrPsiChildren(ScalaElementType.ANNOTATION, JavaArrayFactoryUtil.ScAnnotationFactory)
}