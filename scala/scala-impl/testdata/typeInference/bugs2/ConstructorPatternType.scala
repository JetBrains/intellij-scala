val x: Any = 1

x match {
  case s@Some(t: Int) => {
    /*start*/s/*end*/
  }
}
//Some[Any]