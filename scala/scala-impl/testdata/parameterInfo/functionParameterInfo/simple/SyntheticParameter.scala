def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(<caret>)
// [A <% String : Manifest](x: Int = 45)(implicit ev$1: Int => String, manifest$A$0: Manifest[Int])