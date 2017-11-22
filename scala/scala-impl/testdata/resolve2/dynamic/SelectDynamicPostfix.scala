class D[T] extends Dynamic {
  def applyDynamic(op: String)(args: String*): T = ???
  def selectDynamic(op: String): T = ???
}


val O: D[String] = ???
O /* file: this, name: selectDynamic */ df 
