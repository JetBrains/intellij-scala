object SCL2392B {
  object A {
    val t: AnyRef = new {}
    val tt: A.t.type = /*start*/ t
    /*end*/
  }
}
// A.t.type