object SCL5472C {
  class ParamDefAux[T]
  implicit def forTuple[T](implicit x: ParamDefAux[(T, T, T)]): ParamDefAux[(T, T)] = new ParamDefAux
  implicit def forTuple: ParamDefAux[(String, String)] = new ParamDefAux
  implicit def forTriple[T]: ParamDefAux[(T, T, T)] = new ParamDefAux

  implicit val x: List[Int] = List(1, 2, 3)

  def foo[T, S](implicit t: List[T], x: ParamDefAux[(T, S)]): S = sys.exit()

  /*start*/foo/*end*/
}
//Int