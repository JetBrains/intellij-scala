object SCL7927 {
  trait A {
    def name: String
  }

  class B(val n:String)  extends A {
    def name = n
  }

  object C {
    val ls = List("S", "T", "U")
    val la = /*start*/ls.map(new B(_):A)/*end*/
  }
}
//List[SCL7927.A]