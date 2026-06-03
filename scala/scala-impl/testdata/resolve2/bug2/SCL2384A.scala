object O {
  def blerg = "O.blerg"
}

abstract class B {
  def blerg = "B#Blerg"
}

object C extends B {
  import O._
  /* line: 6 */blerg
}

()
