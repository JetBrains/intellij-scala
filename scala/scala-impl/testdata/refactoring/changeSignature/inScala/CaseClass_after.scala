case class CClass(ii: Int, argss: Int*)(cc: Char, b: Boolean = true) {
  val x = ii + cc + argss.mkString
}
object Test {
  CClass(1)(cc = '2')
  CClass(1, 2, 3)('2')
  CClass(1)('2')
  new CClass(1)(cc = '2')
  new CClass(1, 2, 3)('2')
  new CClass(1)('2')
}