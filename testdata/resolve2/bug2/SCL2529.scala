type x =  {
  type Up
  type X <: /*resolved: true*/Up
  val blerg: AnyRef
  val b: /*resolved: true*/blerg.type
}