class SCL6198 {
  case class Cell[A](value: A)
  case class Row(i: Int)(val values: Cell[_]*)

  val row = Row(1)(Cell("a"), Cell("b"), Cell("c"))

  row.values.foreach {
    cell => println(/*start*/cell/*end*/.value)
  }
}
//SCL6198.this.Cell[_]