def foo(a: String) = 0
implicit def i2s(i: Int) = ""
/*start*/(foo(_))/*end*/ : (String => String) // good code red
//String => String