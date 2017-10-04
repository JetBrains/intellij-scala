class C1 {
  object O
}

class C2 extends C1 {
  println(/* line: 2 */ O.getClass)
  println(classOf[/* resolved: false */ O])
}