class OnlyIf {
  void foo() {
    int i = 1
    /*start*/if (++i == i++) true;/*end*/
  }
}
/*
if (({
  i += 1; i
}) == ({
  i += 1; i - 1
})) true
*/