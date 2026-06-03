trait To[F[_], G[_]] {
  def transform[A](fa: F[A]): G[A]
}

/*start*/Lambda[To[List, Option]].transform(_.headOption)/*end*/
/*To[List, Option]*/