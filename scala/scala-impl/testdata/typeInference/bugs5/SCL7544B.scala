object SCL7544B {
  trait Relationsz {
    type ZeroMany[T]

    def ZeroMany[T](ts: T*): ZeroMany[T]
  }

  class A {
    def foo[DT <: Relationsz {}](dt: DT): Unit = {
      import dt._

      val z: dt.ZeroMany[Int] = /*start*/dt.ZeroMany()/*end*/
    }
  }
}
//dt.ZeroMany[Int]