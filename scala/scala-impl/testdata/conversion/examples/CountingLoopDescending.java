class X {
  void test(int from, int to) {
    for (int i = from; to < i; i=i-1) {
      System.out.println(i);
    }
  }
}
/*
class X {
  def test(from: Int, to: Int): Unit = {
    for (i <- from until to by -1) {
      System.out.println(i)
    }
  }
}*/