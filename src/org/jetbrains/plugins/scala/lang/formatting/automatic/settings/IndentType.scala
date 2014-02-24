package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

/**
 * @author Roman.Shein
 *         Date: 13.11.13
 */
 object IndentType extends Enumeration {
  type IndentType = Value
  val ContinuationIndent, NormalIndent = Value
}
