case class A(x: Int)

new A(3) match {
  case a@A(5) => {
    /*start*/a/*end*/
  }
}
//A