def foo(x: String) = x
class Bar[T <% String](t: T) {
  foo(/*start*/t/*end*/) // type error highlighted.
}
//String