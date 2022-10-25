class JSome(val t: Int //field will be dropped
           ) {
  //comments in droppped constructor
  //before func
  def foo(): Unit = {
    val t: Int = 56 //last in line

    //last in func
  }
  //last in class
}

class MyClass
/* comment for constructor 1 */ {
  /*comment before 1*/
  System.out.println(1)
  /*comment inside 1*/
  System.out.println(2)

  /*comment after 1*/
  /* comment for constructor 2 */
  def this(param: Int) {
    this
    /*comment before 2*/
    System.out.println(3)
    /*comment inside 2*/
    System.out.println(4)
    /*comment after 2*/
  }
}

class MyClass1(x: Int)
//line comment
{
}

class MyClass2(x: Int)
/*block comment*/ {
}

class MyClass3(x: Int)

/** javadoc comment */ {
}

//line comment for class
class MyClass11(x: Int)
//line comment for constructor
{
}

/*block comment for class*/
class MyClass22(x: Int)
/*block comment for constructor*/ {
}

/** javadoc comment for class */
class MyClass33(x: Int)

/** javadoc comment for constructor */ {
}