val strings: Array[String] = Array("a", "ab", "abc")
/*start*/strings zip strings.map(string => string.length)/*end*/
//Array[(String, Int)]