object SCL8689 {

  case class KeyVal[KK, VV](key: KK, value: VV)

  class ReducerComponent[K, V](f: (V, V) => V) {
    var mem = Map[K, V]()

    def apply(kv: KeyVal[K, V]) = {
      val KeyVal(k, v) = kv
      mem += (k -> (if (mem contains k) f(mem(k), v) else v))
    }
  }

  object MemoizeTestMain extends App {

    case class RC[M <: KeyVal[_, _]]() {
      def factory[K, V](implicit ev: KeyVal[K, V] =:= M) = new {
        def apply(f: (V, V) => V) = new ReducerComponent[K, V](f)
      }
    }

    type MapOutput = KeyVal[String, Int]
    val mapFun: (String => MapOutput) = { s => KeyVal(s, 1) }

    val redf = RC[MapOutput].factory
    val red = /*start*/redf.apply(_ + _)/*end*/

    val data = List[String]("a", "b", "c", "b", "c", "b", "b", "c")
    val mapdata = data map mapFun
    mapdata foreach { md => red(md) }
    println(red.mem)
    // OUTPUT: Map(a -> 1, b -> 4, c -> 3)
  }

}
//SCL8689.ReducerComponent[String, Int]