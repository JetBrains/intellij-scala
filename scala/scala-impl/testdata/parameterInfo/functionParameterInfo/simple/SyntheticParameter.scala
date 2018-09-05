def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(<caret>)
// (x: Int = 45)(implicit ev$1: Int => String, manifest$Int: Manifest[Int])