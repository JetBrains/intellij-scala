package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScQuoted}

/** A quoted type
 *
 * val expr = '[ Int ]
 *            |------|
 *           QuotedType
 */
trait ScQuotedType extends ScExpression with ScQuoted {

}
