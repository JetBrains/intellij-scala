object SCL5104 {
  case class Book(title: String, authors: String*)

  /*start*/Book(   //cannot resolve method Book.apply
    "Programming in Modula-2",
    "Wirth, Niklaus"
  )/*end*/
}
//SCL5104.Book