case class PrintedColumn[T](
                             name: String,
                             value: T => Any,
                             color: T => String = { _: T => "blue" })

case class Foo(a: Int, b: String)
val col: PrintedColumn[Foo] = /*start*/PrintedColumn("a", _.a)/*end*/
//PrintedColumn[Foo]