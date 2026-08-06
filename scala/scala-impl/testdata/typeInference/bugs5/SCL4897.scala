val option: Option[Set[String]] = null
def foo[T](): Set[T] = null
val orElse = option.getOrElse(foo())
/*start*/orElse/*end*/
//Set[String]