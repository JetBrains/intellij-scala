class C {
  int [] array1 = new int[3];
  int [] array2 = new int[3];
  void testLabels(){
    Label1:
    for(int i : array1){
        for (int j : array2) {
            if (i > j) {
                break Label1;
            } else {
                continue;
            }
        }
    }
  }
}
/*
class C {
  val array1: Array[Int] = new Array[Int](3)
  val array2: Array[Int] = new Array[Int](3)

  def testLabels(): Unit = {
    Label1 //todo: labels are not supported
    for (i <- array1) {
      for (j <- array2) {
        if (i > j) break Label1 // todo: label break is not supported
        else continue //todo: continue is not supported
      }
    }
  }
}
*/