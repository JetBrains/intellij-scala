package p1
package p2

object O1 {
  object O2 {
    case object CC {
      def f {}
    }
  }
}

trait T {
  println(p1.p2./* line: 4 */O1)
  println(p1.p2.O1./* line: 5 */O2)
  println(p1.p2.O1.O2./* line: 6 */CC)
  println(p1.p2.O1.O2./* line: 6 */CC.getClass)
  println(classOf[p1.p2.O1.O2./* line: 6 */CC])
  println(p1.p2.O1.O2.CC./* line: 7 */f)
}
