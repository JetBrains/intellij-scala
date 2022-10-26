class JSome {
  final int t; //field will be dropped
  JSome(int t){
      this.t = t;
      //comments in droppped constructor
  }
  //before func
  void foo(){
      int t = 56; //last in line
      //last in func
  }
  //last in class
}

public class MyClass {
    /* comment for constructor 1 */
    MyClass() {
        /*comment before 1*/
        System.out.println(1);
        /*comment inside 1*/
        System.out.println(2);
        /*comment after 1*/
    }

    /* comment for constructor 2 */
    MyClass(int param) {
        this();
        /*comment before 2*/
        System.out.println(3);
        /*comment inside 2*/
        System.out.println(4);
        /*comment after 2*/
    }
}

public class MyClass1 {
    //line comment
    MyClass1(int x) {
    }
}

public class MyClass2 {
    /*block comment*/
    MyClass2(int x) {
    }
}

public class MyClass3 {
    /**javadoc comment*/
    MyClass3(int x) {
    }
}

//line comment for class
public class MyClass11 {
    //line comment for constructor
    MyClass11(int x) {
    }
}

/*block comment for class*/
public class MyClass22 {
    /*block comment for constructor*/
    MyClass22(int x) {
    }
}

/**javadoc comment for class*/
public class MyClass33 {
    /**javadoc comment for constructor*/
    MyClass33(int x) {
    }
}