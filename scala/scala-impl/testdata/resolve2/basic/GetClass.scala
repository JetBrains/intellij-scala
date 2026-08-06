1
class A {
  /* */getClass
  this./* */getClass
  super./* */getClass
  super[Object]./* */getClass
  val a: A = new A
  a./* */getClass
  val x: AnyRef = new A
  x./* */getClass
  val z: Any = 1
  z./* resolved: true */getClass

  collection.JavaConversions./* */getClass
}