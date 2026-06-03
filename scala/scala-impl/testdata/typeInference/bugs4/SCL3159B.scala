def foo(x: String) = x
def bar[T <% String](t: T) = {
  foo(/*start*/t/*end*/) // type error highlighted.
}
//String