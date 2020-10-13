package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScTopLevelStubBasedElement
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType

abstract class ScValueOrVariableImpl[V <: ScValueOrVariable](
  stub:     ScPropertyStub[V],
  nodeType: ScPropertyElementType[V],
  node:     ASTNode
) extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScValueOrVariable
    with ScTopLevelStubBasedElement[V, ScPropertyStub[V]]
