object O {
  def applyDynamic(s: String)() {}
}

O./* resolved: false */foo()
