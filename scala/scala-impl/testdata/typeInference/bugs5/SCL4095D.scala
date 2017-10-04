class SqlResult[A]

case class Success[A](x: A) extends SqlResult[A]

class Apply[A]

object Apply {
  def apply[A](f: Int => SqlResult[A]): Apply[A] = new Apply[A]
}

object TestUnit {
  def foo: Apply[Option[Int]] =  Apply { i =>
    if (true) Success(Some(i))
    else /*start*/Success(None)/*end*/
  }
}
//Success[Option[Int]]