package debug

import com.intellij.formatting.Block

object Debug {
  def dump(block: Block) =
    block.toString
}
