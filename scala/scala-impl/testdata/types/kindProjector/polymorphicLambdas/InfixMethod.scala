trait To[F[_], G[_]] {
  def transform[A](fa: F[A]): G[A]
}

/*start*/Lambda[List To Option].transform(_.headOption)/*end*/
/*To[List, Option]*/