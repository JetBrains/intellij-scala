object O extends Dynamic {
  def applyDynamic(i: Int)() {}
}

O./* resolved: false */foo()
