class SCL4375 {
  var qwe: Int => Unit = i => ()

  qwe = _ => ()

  def asd = (i: Int) => ()

  def asd_=(f: Int => Unit) = 1

  asd = /*start*/_ => ()/*end*/
}
//Int => Unit