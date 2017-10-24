object LocalBeforeNamedParams {
  def foo = {
    val namelocal = "sdfsd"
    def printName(nameParam: String = "Unknown") {
      print(nameParam)
    }

    printName(name<caret>)
  }
}