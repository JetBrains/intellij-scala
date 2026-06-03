object BBBB {
  case class A(implicit x: Int)

  /*start*/A.apply()(1)/*end*/
}
//BBBB.A