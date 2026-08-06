object RepeatedParamsResolve {
  def foo(x: Int*) {
    /*start*/x.map {
      case i => i.toString
    }/*end*/
  }
}
//Seq[String]