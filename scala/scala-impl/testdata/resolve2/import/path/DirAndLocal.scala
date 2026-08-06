package a {
  case class C2
}

class C {
  import a._

  println(/* path: a.C1 */C1.getClass)
  println(classOf[/* path: a.C1 */C1])

  println(/* offset: 25, path: a.C2 */C2.getClass)
  println(classOf[/* offset: 25, path: a.C2 */C2])
}