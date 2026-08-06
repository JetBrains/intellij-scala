object SCL6304 {
  class foo(val strings : String*) {
    def foo() = {
      /*start*/(strings ++ List("")).map(Integer.parseInt(_))/*end*/
    }
  }
}
//Seq[Int]