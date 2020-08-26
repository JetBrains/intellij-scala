package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private final class JumpIfNotImpl[Info](override val condition: Value) extends JumpingImpl[Info] with JumpIfNot {
  override protected def asmString: String = s"if not ${condition.valueIdString} jump $targetLabel"
}

