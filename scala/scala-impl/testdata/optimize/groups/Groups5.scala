// Notification message: Rearranged imports
package pack {
class G
}

class Groups1 {
  import scala.collection.mutable.ArrayBuffer
  import java.util.ArrayList
  import pack.G

  val b: ArrayBuffer[Int] = null
  val c: ArrayList[Int] = null
  val d: G = null
}
/*
package pack {
class G
}

class Groups1 {
  import pack.G

  import java.util.ArrayList
  import scala.collection.mutable.ArrayBuffer

  val b: ArrayBuffer[Int] = null
  val c: ArrayList[Int] = null
  val d: G = null
}
 */