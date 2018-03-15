trait To[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}

/*start*/Lambda[List To Option](_.headOption)/*end*/
/*To[List, Option] with Object {
  def apply[T](x: List[T]): Option[T]
}*/