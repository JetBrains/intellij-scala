def foo(a: String) = 0
/*start*/foo(_)/*end*/ : (String => Unit) // good code red
//(String) => Unit