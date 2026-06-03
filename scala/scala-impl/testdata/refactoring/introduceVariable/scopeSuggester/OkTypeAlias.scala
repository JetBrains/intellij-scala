class OkTypeAlias {
  type Q = Int

  trait B {
    type M = Seq[Q]
    val list: /*begin*/List[M]/*end*/ = List(Seq(45))
  }
}
