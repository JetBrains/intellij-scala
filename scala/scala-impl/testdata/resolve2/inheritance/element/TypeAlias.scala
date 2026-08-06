class C1 {
  type A = Int
}

class C2 extends C1 {
  println(/* resolved: false */ A.getClass)
  println(classOf[/* line: 2 */ A])
}