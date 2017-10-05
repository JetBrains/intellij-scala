object InfixApply {
  class Query[T] {
    def apply(x: T): T = x
  }

  def foo[A] : Query[A] = null

  /*start*/InfixApply foo 1/*end*/
}
//Int