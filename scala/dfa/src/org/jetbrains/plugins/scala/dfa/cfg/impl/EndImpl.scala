package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private final class EndImpl[Info] extends NodeImpl[Info] with End {
  override protected def asmString: String = "end"
}
