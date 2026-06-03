class MyRootClass {
  def apply(a: Int): Int = 0
}

object MyRootClass {
  implicit class MyClassWithApply(val c: MyRootClass) {
    def apply(): Int = 0
  }
}

object MyObject extends MyRootClass

object Test {
  /* resolved: true, name: apply */MyObject() // Cannot resolve reference MyObject with such signature
}