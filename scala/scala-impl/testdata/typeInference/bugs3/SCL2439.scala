val subset = for {
  s <- Set("a", "b", "c")
  if(/*start*/s.compareTo("c")/*end*/ < 0)
} yield s
//Int