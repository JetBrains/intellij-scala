package goo

object ImplicitReferenceObject {
  implicit def string2ImplicitReferenceClass(s: String): ImplicitReferenceClass = new ImplicitReferenceClass()
}

class ImplicitReferenceClass {
  def foa: Int = 77
  import ImplicitReferenceObject._
  "".foa
}
/*
package goo

object ImplicitReferenceObject {
  implicit def string2ImplicitReferenceClass(s: String): ImplicitReferenceClass = new ImplicitReferenceClass()
}

class ImplicitReferenceClass {
  def foa: Int = 77
  import ImplicitReferenceObject._
  "".foa
}
*/