object Main {
  case class Cl[T](var x: T)
  def withCl[T, R](v: Cl[T])(body: (Cl[T] => R) { def apply(v: Cl[T]): R }): R = body(v)
  /*start*/withCl(Cl(10)) { (v: Cl[Int]) =>
    v.x = 20
    v.x
  }/*end*/
}
//Int