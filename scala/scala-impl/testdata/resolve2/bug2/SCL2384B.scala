object O {
  def blerg = "O.blerg"
}

abstract class B {
  implicit def blerg = "B#Blerg"
}

object D extends B {
  import O.blerg
  /* line: 6 */blerg
}

()
