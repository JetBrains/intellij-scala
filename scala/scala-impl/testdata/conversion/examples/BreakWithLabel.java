/*start*/
class BreakWithLabel {
    public void main(String[] args) {
        OUTER:
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (i + j % 5 == 0) {
                    // unsupported
                    break OUTER;
                }
            }
        }
    }
}/*end*/

/*
class BreakWithLabel {
  def main(args: Array[String]): Unit = {
    OUTER //todo: labels are not supported
    var i: Int = 0
    while ( {
      i < 10
    }) {
      var j: Int = 0
      while ( {
        j < 10
      }) {
        if (i + j % 5 == 0) { // unsupported
          break OUTER // todo: label break is not supported

        }

        {
          j += 1; j - 1
        }
      }

      {
        i += 1; i - 1
      }
    }
  }
}*/