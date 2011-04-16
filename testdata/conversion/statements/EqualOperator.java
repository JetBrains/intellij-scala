class EqualOperator {
  void foo() {
    /*start*/test(1 == 1, 1 != 1, "" == "", "" != "")/*end*/
  }
}
//test(1 == 1, 1 != 1, "" eq "", "" ne "")