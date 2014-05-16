class A {
  class G

  trait B

  class K[T >: G with B] {
    def z(t: T) = 1
    def z(s: String) = "text"

    /*start*/z(new G with B)/*end*/
  }
}
//Int