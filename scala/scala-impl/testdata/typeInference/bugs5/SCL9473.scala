object SCL9473 {
  trait Sys[S <: Sys[S]] {
    type I

    def foo(tx: Any): Int
  }

  def prepare[S <: Sys[S], I1 <: Sys[I1]](system: S { type I = I1 }): Any = {
    /*start*/system.foo(123)/*end*/
  }
}
//Int