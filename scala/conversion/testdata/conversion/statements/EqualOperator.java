class EqualOperator {
  void foo() {
    /*start*/test(1 == 1, 1 != 1, "" == "" && "" != "a", "" != "");/*end*/
  }
}
//test(1 == 1, 1 != 1, ("" eq "") && ("" ne "a"), "" ne "")