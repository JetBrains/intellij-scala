object SCL8989 {
  class C[T](value: T) {
    class I {
      def t: T = value
    }
  }

  class B[T](value: T) extends C[T](value) {
    class I extends super.I

    val i = new I
  }

  object Main {
    val i : B[String]#I = ???

    /*start*/i.t.substring(1)/*end*/
  }
}
//String