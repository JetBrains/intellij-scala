class X {
  boolean match(int[] arr1, int[] arr2, int offset, int count) {
    for (int i = 0; i < count; i++, offset++) {
      if (arr1[i] != arr2[offset]) return false;
    }
    return true;
  }
}
/*
class X {
  def `match`(arr1: Array[Int], arr2: Array[Int], offset: Int, count: Int): Boolean = {
    var i: Int = 0
    while (i < count) {
      if (arr1(i) != arr2(offset)) return false
      i += 1
      offset += 1
    }
    true
  }
}
*/