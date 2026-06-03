object SCL7954 {

  trait Base {
    type HKT[X]
    val value: HKT[Int]

    def f(n: Int): (Base {type HKT[X] = Base.this.HKT[X]})
  }

  class Derived(val value: Option[Int]) extends Base {
    type HKT[X] = Option[X]

    def f(n: Int) = /*start*/new Derived(Some(n * 2))/*end*/
  }

}
//SCL7954.Derived