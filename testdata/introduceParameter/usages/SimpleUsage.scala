object test {
  foo

  def foo = {
    /*start*/1/*end*/ + 2
  }
}
/*
object test {
  foo(1)

  def foo(param: Int) = {
    /*start*/param/*end*/ + 2
  }
}
*/