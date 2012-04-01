trait Category[~>[_, _]] {
  def compose(f: Int ~> Int){}
}

trait M[_, _]

new Category[M] {
  override def compose(f: Int M Int) = super./*line:2 */compose(f)
}
