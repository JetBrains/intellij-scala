object X {
  def apply(i: Int): Option[X] = {
    if (i > 0) Some(new X(i))
    else None
  }
}

final case class X(i: Int)

object Main extends App{
  /*start*/X(2)/*end*/
}

//Option[X]