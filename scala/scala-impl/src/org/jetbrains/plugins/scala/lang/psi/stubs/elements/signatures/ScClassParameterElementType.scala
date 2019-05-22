package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */
final class ScClassParameterElementType extends ScParamElementType[ScClassParameter]("class parameter") {

  override def createPsi(stub: ScParameterStub) = new ScClassParameterImpl(stub)

  override def createElement(node: ASTNode) = new ScClassParameterImpl(node)

  override def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {
    sink.occurrences(CLASS_PARAMETER_NAME_KEY, stub.getName)
    ImplicitInstanceIndex.occurrence(sink, stub.implicitType)
  }
}