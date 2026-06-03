object O1 {
  override def equals(that: Any): Boolean = {true}
}

object O2

O1 /* file: AnyRef */ != O2
O2 /* file: AnyRef */ != O1

