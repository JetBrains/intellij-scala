def get[T <: String](implicit x: T) = x

def foo(implicit x: String) {
  /*start*/get/*end*/
}
//String