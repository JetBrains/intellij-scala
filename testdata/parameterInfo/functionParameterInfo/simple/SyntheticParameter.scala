def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(/*caret*/)
// implicit ev1: (Int) => String, ev2: Manifest[Int]