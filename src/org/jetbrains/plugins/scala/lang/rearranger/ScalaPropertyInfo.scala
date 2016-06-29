package org.jetbrains.plugins.scala
package lang.rearranger

/**
 * @author Roman.Shein
 * Date: 31.07.13
 */
class ScalaPropertyInfo (val getter: ScalaArrangementEntry,
                             val setter: ScalaArrangementEntry) {
  def isComplete: Boolean = getter != null && setter != null
}