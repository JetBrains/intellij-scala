class X {
  void test(int from, int to) {
    for (int i = from; i <= to; i+=1) {
      System.out.println(i);
    }
  }
}
/*
class X {
  def test(from: Int, to: Int): Unit = {
    for (i <- from to to) {
      System.out.println(i)
    }
  }
}*/