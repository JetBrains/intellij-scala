for ((a: String, b: String) <- Map[String, String]();
     c: String <- List[String]();
     val d: String = a.toString)
yield (/* offset: 6 */ a + /* offset: 17 */ b + /* offset: 59 */ c + /* offset: 97 */ d)

