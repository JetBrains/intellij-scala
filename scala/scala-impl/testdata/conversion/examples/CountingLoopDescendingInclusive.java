class X {
  void test(int from, int to) {
    for (int i = from; to <= i; --i) {
      System.out.println(i);
    }
  }
}
/*
class X {
  def test(from: Int, to: Int): Unit = {
    for (i <- from to to by -1) {
      System.out.println(i)
    }
  }
}*/