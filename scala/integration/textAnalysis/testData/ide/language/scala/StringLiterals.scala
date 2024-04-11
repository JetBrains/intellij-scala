class Main {
  "It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human";
  "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
  "It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings";
  "It is ${1} friend";
  "It is friend. But I have a ${1} here";

  """
    |Lorem ipsum dolor sit amet,
    |<TYPO descr="Typo: In word 'onsectetur'">onsectetur</TYPO>...
    |""".stripMargin;

  """
    |Lorem ipsum dolor sit amet,
    |<TYPO descr="Typo: In word 'onsectetur'">onsectetur</TYPO>...
    |""".stripMargin;

  System.out.println("It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human");
  System.out.println("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
  System.out.println("It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings");
  System.out.println("It is ${1} friend");
  System.out.println("It is friend. But I have a ${1} here");
  System.out.println("The path is ../data/test.avi");

  "(cherry picked from "; // hard-coding the string git outputs
  "I'd like to <GRAMMAR_ERROR descr="EN_COMPOUNDS_CHERRY_PICK">cherry pick</GRAMMAR_ERROR> this";
}
