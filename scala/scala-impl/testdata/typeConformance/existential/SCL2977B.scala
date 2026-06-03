class F[-A]
val l: F[T] forSome { type T >: Int} = new F[AnyVal]

// True