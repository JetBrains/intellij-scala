class C1(p: Int)

class C2 extends C1(1) {
  println(/* resolved: false */ p)
}