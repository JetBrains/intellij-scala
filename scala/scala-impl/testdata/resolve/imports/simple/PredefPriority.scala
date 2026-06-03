object U {
  object Set {
    def empty: Int = 45
  }
}

object Test {
  import U.Set

  val i: Int = <ref>Set.empty
}