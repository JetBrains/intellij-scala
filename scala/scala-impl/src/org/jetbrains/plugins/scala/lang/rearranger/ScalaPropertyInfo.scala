package org.jetbrains.plugins.scala
package lang.rearranger

private class ScalaPropertyInfo(val getter: ScalaArrangementEntry,
                                val setter: ScalaArrangementEntry) {
  def isComplete: Boolean = getter != null && setter != null
}