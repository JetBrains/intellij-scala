class C1(val p: Int)

class C2 extends C1(1) {
  println(/* line: 1 */ p)
}