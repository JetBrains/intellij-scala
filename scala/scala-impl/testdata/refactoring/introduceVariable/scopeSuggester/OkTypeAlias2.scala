class OkTypeAlias2 {
  type Q = Int

  trait B {
    val list: List[/*begin*/Q/*end*/] = List(45)
  }
}


