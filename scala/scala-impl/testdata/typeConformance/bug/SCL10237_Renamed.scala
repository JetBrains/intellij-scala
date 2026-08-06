trait Unapply[TC[_[_]], MA] extends Serializable {
  type M[_]
  type A
}

object Unapply {
  type Aux1[TC[_[_]], MA, F[_], AA] = Unapply[TC, MA] {
    type M[X] = F[X]
    type A = AA
  }

  implicit def unapply1[TC[_[_]], F[_], AA](implicit tc: TC[F]): Unit = {
    /*caret*/
    val a: Aux1[TC,F[AA],F,AA] = new Unapply[TC,F[AA]] {
      type M[Y] = F[Y]
      type A = AA
    }
  }
}
//True