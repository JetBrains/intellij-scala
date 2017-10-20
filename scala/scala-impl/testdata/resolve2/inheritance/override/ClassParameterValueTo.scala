class C1(p: Int)

class C2(val p: Int) extends C1(1) {
  println(/* line: 3 */p)
}