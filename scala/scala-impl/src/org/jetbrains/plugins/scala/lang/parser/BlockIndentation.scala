package org.jetbrains.plugins.scala
package lang
package parser

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * Manages the indentation in a block.
 *
 * Even a block has an indentation width, which is important
 * if indentation style is used inside of a block.
 *
 * object Test { def a = 0  // <- no error, the whitespaces before the def have no effect on indentation
 *     def b = 0            // <- even though it is not the first statement, its indentation is used for the whole block
 *         object Inner     // <- Belongs to Test. It's indentation is irrelevant
 *       def c = 0          // <- belongs to inner because indentation greater than b's (indentation of Inner doesn't matter)
 *     def d = 0            // <- belongs to Test because indentation not greater than b's
 * }
 *
 * This class is used to push the first indentation width that is found in a block
 * onto the indentation stack and remove it afterwards.
 */
trait BlockIndentation {
  def fromHere()(implicit builder: ScalaPsiBuilder): Unit
  def drop()(implicit builder: ScalaPsiBuilder): Unit
}

object BlockIndentation {
  object noBlock extends BlockIndentation {
    override def fromHere()(implicit builder: ScalaPsiBuilder): Unit = ()
    override def drop()(implicit builder: ScalaPsiBuilder): Unit = ()
  }

  def create: BlockIndentation = new BlockIndentation {
    private var hasIndentation = false
    override def fromHere()(implicit builder: ScalaPsiBuilder): Unit = {
      if (!hasIndentation) {
        builder.findPreviousIndent foreach {
          indent =>
            builder.pushIndentationWidth(indent)
            hasIndentation = true
        }
      }
    }

    override def drop()(implicit builder: ScalaPsiBuilder): Unit = {
      if (hasIndentation) {
        builder.popIndentationWidth()
      }
    }
  }
}