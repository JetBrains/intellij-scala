class AA {
  def foo: AA = new AA

  def b(): BB = new BB
}

class BB extends AA {
  override def foo = {
    z.b()
  }

  def z = /*start*/foo/*end*/

  foo
}
//AA