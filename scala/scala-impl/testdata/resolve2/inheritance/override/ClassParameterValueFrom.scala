class C1(val p: Int)

class C2(p: Int) extends C1(1) {
  println(/* line: 3 */p)
}