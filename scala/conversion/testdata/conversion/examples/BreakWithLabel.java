class BreakWithLabel {
    public void main(String[] args) {
        OUTER:
        for (int i = 0; i < 10; i++) { //comment 1
            for (int j = 0; j < 10; j++) {
                if (i + j % 5 == 0) {
                    // unsupported
                    break OUTER;
                }
            }
        }
    }
}
/*
class BreakWithLabel {
  def main(args: Array[String]): Unit = {
    OUTER //todo: labels are not supported
    for (i <- 0 until 10) { //comment 1
      for (j <- 0 until 10) {
        if (i + j % 5 == 0) {
          // unsupported
          break OUTER // todo: label break is not supported
        }
      }
    }
  }
}*/