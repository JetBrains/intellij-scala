def foo[A <% String : Manifest](x: Int = 45) = x

foo[Int]()(<caret>)
//TEXT: [A <% String: Manifest](x: Int = …)(using ev$1: Int => String, manifest$A$0: Manifest[Int]), STRIKEOUT: false