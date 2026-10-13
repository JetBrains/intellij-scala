package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * A Spliced Block {{{
 * val x = ${ test }
 *         |-------|
 *         SplicedBlock
 *
 * val y: ${ test } = ???
 *        |-------|
 *        SplicedBlock
 * }}}
 */
trait ScSplicedBlock extends ScBlock
  with ScTypeElement
  with ScSpliced {

  // as a type element, it is typed against the expected type computed from its context
  override protected def innerType: TypeResult = innerType(defaultExpectedType)
}
