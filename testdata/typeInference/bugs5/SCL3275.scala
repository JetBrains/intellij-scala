object SCL3275 {

  trait Base {
    def f: (Int) => Double
  }

  class Derived(val f: (Int) => Double) extends Base {
    val x = /*start*/f(0) > 0.0/*end*/ // f(0) is red
  }

}
//Boolean