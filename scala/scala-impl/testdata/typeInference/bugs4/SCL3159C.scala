def foo(x: String) = x
type Stringable[T] = (T => String)
class Bar[T : Stringable](t: T) {
  foo(/*start*/t/*end*/) // type error highlighted.
}
//String