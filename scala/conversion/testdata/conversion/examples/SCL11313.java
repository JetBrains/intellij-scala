class Test {
    boolean booleanMethod() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        !booleanMethod();
        return super.equals(o);
    }

    boolean test(Integer b1, Integer b2) {
        return !b1.equals(b2);
    }
}

/*
class Test {
  def booleanMethod: Boolean = false

  override def equals(o: AnyRef): Boolean = {
    !booleanMethod
    super.equals(o)
  }

  def test(b1: Integer, b2: Integer): Boolean = !(b1 == b2)
}
*/