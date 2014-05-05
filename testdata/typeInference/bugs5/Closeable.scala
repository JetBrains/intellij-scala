package one.two.three

import _root_.scala.collection.mutable.HashSet

package object scala {
  type HashSet = {
    def close(): Int
  }

  def using: HashSet = null

  /*start*/using.close()/*end*/
}
//Int