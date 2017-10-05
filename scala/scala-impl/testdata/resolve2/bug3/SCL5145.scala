import language.dynamics

class Foo extends Dynamic {
  def selectDynamic(name: String) {
    println("selectDynamic: " + name)
  }
  def applyDynamic(name: String)(args: Any*): String = {
    println("applyDynamic: " + name)
    "test"
  }
  def applyDynamicNamed(name: String)(args: (String, Any)*) {
    println("applyDynamicNamed: " + name)
  }
  def updateDynamic(name: String)(value: Any) {
    println("updateDynamic: " + name)
  }
}

object Test {
  def main(args: Array[String]) {
    var foo = new Foo
    foo./* name: applyDynamic */bar(5)
    foo /* name: applyDynamic */bar 5
    foo. /* name: applyDynamicNamed */bar(x = 4, 6)
    foo /* name: applyDynamicNamed */bar (x = 5)
    foo./* name: updateDynamic */bar = 1
  }
}