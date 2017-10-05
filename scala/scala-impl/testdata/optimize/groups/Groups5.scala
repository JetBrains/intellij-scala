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
  import java.util.ArrayList

  import pack.G

  import scala.collection.mutable.ArrayBuffer

  val b: ArrayBuffer[Int] = null
  val c: ArrayList[Int] = null
  val d: G = null
}
 */