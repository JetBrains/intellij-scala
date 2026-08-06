object SCL6787 {
  def foo: List[Int] = List(1, 2, 3)

  /*start*/(this.foo _).apply()/*end*/
}
//List[Int]