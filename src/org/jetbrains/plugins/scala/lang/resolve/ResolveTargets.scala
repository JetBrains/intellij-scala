package org.jetbrains.plugins.scala
package lang
package resolve

object ResolveTargets extends Enumeration {
  val METHOD, VAR, VAL, OBJECT, CLASS, PACKAGE = Value
}