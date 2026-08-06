def foo(a: String) = 0
val z: String => Unit = /*start*/foo(_)/*end*/ // good code red
//String => Unit