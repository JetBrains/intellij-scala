def foo(s: String => String => String) = s(s("")(""))
foo(x => y => x + /*start*/y/*end*/)
//String