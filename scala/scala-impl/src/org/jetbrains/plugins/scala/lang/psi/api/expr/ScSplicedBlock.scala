package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/** A Spliced Block
 *
 * val x = ${ test }
 *         |-------|
 *        SplicedBlock
 *
 * val x: ${ test } = ???
 *        |-------|
 *       SplicedBlock
 */
trait ScSplicedBlock extends ScBlock with ScTypeElement with ScSpliced {

}
