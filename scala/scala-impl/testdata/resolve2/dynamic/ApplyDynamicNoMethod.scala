object O extends Dynamic {
  def fooDynamic(i: Int)() {}
}

O./* resolved: false */foo()
