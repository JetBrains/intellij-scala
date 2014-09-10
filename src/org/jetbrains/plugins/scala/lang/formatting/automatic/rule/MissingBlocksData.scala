package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock

/**
 * Created by Roman.Shein on 11.08.2014.
 */
class MissingBlocksData(val parentBlock: ScalaBlock, val position: Int) {
}

object MissingBlocksData {
  def apply(parentBlock: ScalaBlock, position: Int) = new MissingBlocksData(parentBlock, position)
}