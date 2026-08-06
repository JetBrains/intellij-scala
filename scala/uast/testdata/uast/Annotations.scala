class MyAnnotation(value: String, name: String = "", count: Int = 0, cls: Class[_] = null)
  extends scala.annotation.StaticAnnotation

@MyAnnotation("abc")
class A1 {}

@MyAnnotation(value = "ghi", name = "myName", count = 123, cls = null)
class A3 {}