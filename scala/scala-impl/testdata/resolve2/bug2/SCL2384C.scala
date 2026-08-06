object O {
  val blerg = "O.blerg"
}

abstract class B {
  final val blerg = "B#Blerg"
}

object D extends B {
  import O.blerg
  identity[/* line: 6 */blerg.type](blerg)
}

()
