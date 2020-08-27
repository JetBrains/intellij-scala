package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private final class PhiValueImpl[SourceInfo](incoming: Map[Value, Seq[Block]]) extends ValueImpl[SourceInfo] with PhiValue {
  override protected def asmString: String = s"$labelString <- ${incoming.keys.mkString(" | ")}"
}
