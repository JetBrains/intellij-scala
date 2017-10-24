object Test {
  case class TestClass(size: String)

  implicit class TestClassObjectHelper(val testClass: TestClass.type) {
    def apply(size: Int): TestClass = TestClass(size.toString)
  }

  /* resolved: true, name: apply */TestClass(1)
}