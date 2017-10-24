val fun2: (Int*) ⇒ Int = args ⇒ { /*start*/args/*end*/.reduce((a, b) ⇒ a+b)}
val res = fun2(1,1,1,1,1)
println(s"Res= $res")

//Seq[Int]