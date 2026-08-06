object ClassParameterResolveTwo {
  class Parent {
    var name: String = "parent"
  }

  class Child(name: String) extends Parent {
    def getThisName = this./* line: 6*/name
  }

  object Main {
    val hc = new Child("text")
    hc. /* line: 3 */ name
  }
}