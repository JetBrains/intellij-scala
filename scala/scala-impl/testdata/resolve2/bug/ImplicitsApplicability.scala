object ImplicitsApplicability {
  class V

  object V {
    implicit def v2t(v: V): T = error("")
  }

  class U { def u = 0}

  class T { def t = 0}
  object T {
    implicit def t2u(t: T): U = error("")
    implicit def u2t(u: U): T = error("")
  }

  // Search scope of implicit view should be the parts of the type T
  (new T)./* */u

  def foo(s: String) = 45
  def foo(t: T) = 0
  // Search scope of implicit view should be the parts of the U => T
  /* */foo(new U)

  // Search scope of implicit view should be the parts of the V => T
  /* */foo(new V)
}
