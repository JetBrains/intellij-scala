class PrivateEnclosing {
  class P {
    object Q {
      private[P] val t = 45
    }
    val z = Q./*ref*/t
  }
}
//true