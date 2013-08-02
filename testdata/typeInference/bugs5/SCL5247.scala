object SCL5247 extends App {

  var msg = "hello"

  val msgUpdater = msg_= _ // This is red with type aware highlighting

  /*start*/msgUpdater("bye")/*end*/

  println(msg)

}
//Unit