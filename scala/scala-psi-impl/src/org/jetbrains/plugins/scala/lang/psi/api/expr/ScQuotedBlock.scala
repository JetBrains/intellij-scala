package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * A Quoted block {{{
 * val expr = '{ quoted expr }
 *            |--------------|
 *            ScQuotedBlock
 * }}}
 */
trait ScQuotedBlock extends ScExpression
  with ScQuoted
  with ScBlock
