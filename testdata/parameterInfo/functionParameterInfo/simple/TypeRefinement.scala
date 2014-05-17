def id[A, B](a: A, b: B)
id[({type A >: Int <: AnyVal})#A, ({type B = Int})#B](/*caret*/)
//a: ({type A >: Int <: AnyVal})#A, b: Int