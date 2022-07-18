// Notification message: Removed 4 imports
object A {
  class X
  class Y

  implicit val s: String = ""
}

object B1 {
  import A.{X => Z, s => _}
  new Z
}

object B2 {
  import A.{Y, X => Z, s => _, _}
  (new Y, new Z)
}

object B3 {
  import A.{X => _, _}
  new Y
}

object B4 {
  import A.{s => implicitString, X => Z, _}
  (new Y, new Z)
}

object B5 {
  import A.{s => implicitString, X => Z, _}

  def foo(implicit s: String) = s
  foo

  new Y
}

object B6 {
  import A.{Y, X => Z, s => _, _}
  (new Y, new Z)
}

/*
object A {
  class X
  class Y

  implicit val s: String = ""
}

object B1 {
  import A.{X => Z, s => _}
  new Z
}

object B2 {
  import A.{Y, X => Z, s => _}
  (new Y, new Z)
}

object B3 {
  import A.{X => _, _}
  new Y
}

object B4 {
  import A.{X => Z, _}
  (new Y, new Z)
}

object B5 {
  import A.{s => implicitString, _}

  def foo(implicit s: String) = s
  foo

  new Y
}

object B6 {
  import A.{Y, X => Z, s => _}
  (new Y, new Z)
}
*/