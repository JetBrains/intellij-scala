class Parent {
        def name: String = "parent"
}

class Child(name: String) extends Parent {
        def getThisName = this./* line: 5 */name     // "name" is not resolved
}