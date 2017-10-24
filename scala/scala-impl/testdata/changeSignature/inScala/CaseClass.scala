case class <caret>CClass(i: Int, c: Char, args: Int*) {
  val x = i + c + args.mkString
}
object Test {
  CClass(1, c = '2')
  CClass(1, '2', 2, 3)
  CClass(1, '2')
  new CClass(1, c = '2')
  new CClass(1, '2', 2, 3)
  new CClass(1, '2')
}