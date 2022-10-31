object SCL5571 {

  class Foo[A1] extends Bar[A1] {}

  trait Bar[A] {
    this: Foo[A] =>

    private val m: Map[A, Int] = Map.empty

    def flaf(a: A) {
      m.get(/*start*/a/*end*/)
    }
  }

}

//A