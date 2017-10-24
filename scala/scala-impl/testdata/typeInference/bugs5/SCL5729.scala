object SCL5729 {
  import scala.collection.immutable

  class IAM[T](val self: immutable.TreeMap[Int,T]) {
    def this(it :Iterable[(Int,T)]) = this(null)
    def this() = this(immutable.TreeMap[Int,T]())

    val z = new IAM(immutable.TreeMap[Int,T]())
    /*start*/z/*end*/
  }
}
//SCL5729.IAM[T]