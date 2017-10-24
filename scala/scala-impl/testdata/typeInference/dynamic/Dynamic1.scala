class Aa extends Dynamic {
  def applyDynamicNamed(name: String)(v: (String, Any)) = "something"
}
val a = new Aa
var x = "text"
/*start*/a.foo(x = "something")/*end*/
//String