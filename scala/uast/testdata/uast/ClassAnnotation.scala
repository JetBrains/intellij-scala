@Test
class A

class Test extends scala.annotation.StaticAnnotation

class MyAnnotation(val text: String) extends scala.annotation.StaticAnnotation

@MyAnnotation("class")
class B {

    @MyAnnotation("inB class")
    class InB {

    }

}

@MyAnnotation("companion")
object B {

}

@MyAnnotation("object")
object Obj