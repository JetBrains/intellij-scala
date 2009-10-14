object LongCalls {
  def foo(x: String => String) = x("45")

  foo(/*start*/_.substring(1).concat("556")/*end*/)
}
//(String) => String