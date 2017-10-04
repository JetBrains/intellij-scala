object X {
  def apply(i: Int): Option[X] = {
    if (i > 0) Some(new X(i))
    else None
  }
}

final case class X(i: Int)

class C {
  X(2).<ref>foreach(x => println(x)) //compiles and prints 2 - IntelliJ doesn't recognise that X(2) is an Option, foreach is red
}