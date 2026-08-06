object O extends Dynamic {
  def applyDynamic(s: String)() {}
}

O./* file: this, name: applyDynamic */foo()
