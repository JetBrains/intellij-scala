class C1 {
  object O
}

class C2 extends C1 {
  object O

  println(/* resolved: false */ O.getClass)
}
