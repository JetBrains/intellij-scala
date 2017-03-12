def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(<caret>)
// implicit ev$1: (Int) => String, ev$2: Manifest[Int]