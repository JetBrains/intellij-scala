class C {
  import b1.b2._

  println(/* path: b1.b2.C3 */C3.getClass)
  println(classOf[/* path: b1.b2.C3 */C3])
}