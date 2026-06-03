object A {
  def cachedWithoutModificationCount[T1, R](
    wrapper: ValueWrapper[R], f: T1=> R
  ): R = {
    ???
  }

  def cachedWithoutModificationCount[T1, T2, R](
    wrapper: ValueWrapper[R], f: (T1, T2) => R
  ): R = {
    ???
  }


  trait ValueWrapper[A]
  def softerReference[A]: ValueWrapper[A] = ???

  def foo = ca<ref>chedWithoutModificationCount(
    softerReference,
    (tp: String, compoundTypeThisType: Option[String]) => 1
  )
}