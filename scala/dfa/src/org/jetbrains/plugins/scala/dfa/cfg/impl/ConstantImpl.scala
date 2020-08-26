package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private final class ConstantImpl[Info](override val constant: DfAny) extends ValueImpl[Info] with Constant {
  override protected def asmString: String = s"$valueIdString <- $constant"
}
