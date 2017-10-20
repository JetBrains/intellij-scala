object ls {
  def collect[A](x: PartialFunction[Int, A]): A = null
}
/*start*/(ls.collect){ case x => x }/*end*/

//Int