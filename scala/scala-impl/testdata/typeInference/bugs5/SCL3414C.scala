class AA {
  def foo: AA = new AA

  def b(): BB = new BB
}

class BB extends AA {
  override def foo = {
    /*start*/z/*end*/.b()
  }

  def z = foo

  foo
}
//AA