class Parameterless {
  def bar(i: Int = 1) = 1

  bar()
  bar()
  this bar()
  this.bar()
}

class Child1 extends Parameterless {
  override def bar(i: Int = 1) = 2

  bar()
}

class Child2 extends Parameterless {
  override def bar(i: Int = 1) = 3

  bar()
}

class Child3 extends Parameterless {
  override def bar(i: Int = 1) = 4

  bar()
}