package member

trait GivenRefinement {
  trait T {
    type A
  }

  given alias: (T { type A = Int }) = ???

  given (T { type A = String }) = ???

  given aliasTypeParameter[B]: (T { type A = B }) = ???

  given aliasValueParameter(using x: Int): (T { type A = Int }) = ???

  object AnonymousTypeParameter {
    given [B]: (T { type A = B }) = ???
  }

  object AnonymousValueParameter {
    given (using x: Int): (T { type A = Int }) = ???
  }

  val value: T { type A = Int } = ???

  def method: T { type A = Int } = ???
}