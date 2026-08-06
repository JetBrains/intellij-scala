object O extends Dynamic {
  def applyDynamic(i: Int)() {}
}

O./* line: 2, applicable: false, name: applyDynamic*/foo()
