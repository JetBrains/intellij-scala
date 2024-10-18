class B {
  trait M {
    type A
  }

  trait Base {
    type T
  }
}

class Repro extends B {
  trait B extends Base {
    type T

    def t: T
  }

  type M_A[M1 <: M] = M1#A

  def test(value: (B{ type T = Repro }) with M_A[M { type A = B }]) = value.t./*resolved: true*/ok

  def ok = ""
}
