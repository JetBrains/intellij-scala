class Switch {
    public static void main(String[] args) {

        /*start*/int month = 1;
        String monthString;
        switch (month) {
            case 1:  monthString = "January";
            case 2:  monthString = "February";
            default: monthString = "Invalid month";
        }/*end*/
    }
}

/*
val month: Int = 1
var monthString: String = null
month match {
  case 1 =>
    monthString = "January"
  case 2 =>
    monthString = "February"
  case _ =>
    monthString = "Invalid month"
}
*/