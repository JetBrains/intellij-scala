trait Super[T]
case class Sub[T](v: T) extends Super[T]
def upgrade[I, O](f: I => Set[O]): I => Set[Super[O]] = f andThen (z => /*start*/z.map(Sub(_))/*end*/)
//Set[Super[O]]