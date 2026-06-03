object enum extends Enumeration {
  val a1, a2: Value = Value
}
import enum._

val a: Set[Value] = ValueSet(a1, a2)
//True