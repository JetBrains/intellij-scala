class Annotation(val strings: String*) extends scala.annotation.StaticAnnotation

@Annotation
class A

class AnnotationInner(val value: Annotation) extends scala.annotation.StaticAnnotation

@AnnotationArray(new Annotation())
class B1

@AnnotationArray(value = new Annotation("sv1", "sv2"))
class B2

class AnnotationArray(val value: Annotation*) extends scala.annotation.StaticAnnotation

@AnnotationArray(new Annotation(strings = Array("sar1", "sar2"): _*))
class C