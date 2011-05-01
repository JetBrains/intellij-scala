def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(/*caret*/)
// implicit evidence$1: (Int) => String, evidence$2: Manifest[Int]