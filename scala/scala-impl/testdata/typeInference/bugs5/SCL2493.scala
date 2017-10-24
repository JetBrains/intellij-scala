class S {
  trait Apply[A] {
    def apply(a: A): A = a
  }

  def foo[A]: Apply[A] = null

  val x = foo(1) // scalac infers the type argument 'A' as 'Int', plugin does not infer this.
  /*start*/x/*end*/
}
//Int