class C1[T]

class C2 extends C1[Int] {
  println(/* resolved: false */ T)
}