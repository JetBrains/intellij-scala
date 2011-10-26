case class ImplData( s:String )
class Err {
  def doSomething( implicit v:ImplData ) = v.s
  def error{
    ImplData( "1" )::ImplData( "2" )::Nil foreach{
      implicit data => // <===================================
        doSomething
      /*start*/doSomething/*end*/
    }
  }
}
//String