trait LegacyLiftable[T] {
  def apply(universe: scala.reflect.api.Universe, value: T): universe.Tree
}
