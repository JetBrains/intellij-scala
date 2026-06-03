trait Category {
  type ~>[A, B]
  def compose(f: Int ~> Int){}
}

trait M[_, _]

new Category {
  type ~>[A, B] = M[A, B]
  override def compose(f: Int M Int) = super./*line: 3*/compose(f)
}
