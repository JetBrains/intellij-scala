object SCL2806 {
  trait A
  trait B
  class C extends A with B
  var z: A = null
  new C match {
    case x: B =>
      /*start*/x/*end*/
  }
}
//SCL2806.C