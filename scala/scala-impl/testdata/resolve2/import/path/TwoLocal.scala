package local {
  case class L1
}

package local {
  case class L2
}

class C {
  import local._

  println(/* offset: 29, path: local.C1 */L1.getClass)
  println(classOf[/* offset: 29, path: local.C1 */L1])

  println(/* offset: 64, path: local.C2 */L2.getClass)
  println(classOf[/* offset: 64, path: local.C2 */L2])
}