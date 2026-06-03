// Notification message: Rearranged imports
package pack {
  class G
}

import scala.collection.mutable.ArrayBuffer
import java.util.ArrayList
import pack.G

class Groups1 {
  val b: ArrayBuffer[Int] = null
  val c: ArrayList[Int] = null
  val d: G = null
}
/*
package pack {
  class G
}

import pack.G

import java.util.ArrayList
import scala.collection.mutable.ArrayBuffer

class Groups1 {
  val b: ArrayBuffer[Int] = null
  val c: ArrayList[Int] = null
  val d: G = null
}
 */