package scatch

sealed trait Identity {
  def zero: Int = 0
}

object A {
  implicit def IdentityTo[A](x: A) = new Identity
}
object B {
  implicit def IdentityTo[A](x: A) = new Identity {
    val value = null
  }
}

object Scratch {
  {
    import A._
    "A".zero
  }
  {
    import B._
    "B".zero
  }
}
/*package scatch

sealed trait Identity {
  def zero: Int = 0
}

object A {
  implicit def IdentityTo[A](x: A) = new Identity
}
object B {
  implicit def IdentityTo[A](x: A) = new Identity {
    val value = null
  }
}

object Scratch {
  {
    import A._
    "A".zero
  }
  {
    import B._
    "B".zero
  }
}*/