case class MyClass(field1: Int, field2: String) {
  def doMethod() {}
}

val myClass = MyClass(22, "hello")
val myClass1 = myClass copy(/* */field2 = "There", /* */field1 = 33)