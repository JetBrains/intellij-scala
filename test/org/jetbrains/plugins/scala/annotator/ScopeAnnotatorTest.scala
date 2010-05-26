package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language

/**
 * Pavel.Fatin, 18.05.2010
 */

class ScopeAnnotatorTest extends SimpleTestCase {
  // TODO binding patterns, alias, val a, a = ; val (a, a) = 
  // TODO for comprehension
  // TODO function literal arguments
  // TODO class C(val) { val }
  // TODO constructors
  // TODO imports and renaming
  // TODO id and scope names?  messages, "C is already defined as type C"
  
  def testEmpty {
    assertMatches(messages("")) { 
      case Nil => 
    }
  }

  def testSingleDefinition {
      assertMatches(messages("class C")) { 
      case Nil =>  
    }
    assertMatches(messages("case class C")) { 
      case Nil =>  
    }
    assertMatches(messages("trait T")) {
      case Nil =>  
    }
    assertMatches(messages("object O")) { 
      case Nil =>  
    }
    assertMatches(messages("case object O")) { 
      case Nil =>  
    }
    assertMatches(messages("package p {}")) { 
      case Nil =>  
    }
    assertMatches(messages("val v = null")) { 
      case Nil =>  
    }
    assertMatches(messages("val v")) { 
      case Nil =>  
    }
    assertMatches(messages("var v = null")) { 
      case Nil =>  
    }
    assertMatches(messages("var v")) { 
      case Nil =>  
    }
    assertMatches(messages("type A = Any")) { 
      case Nil =>  
    }
    assertMatches(messages("type A")) { 
      case Nil =>  
    }
    assertMatches(messages("def f {}")) { 
      case Nil =>  
    }
    assertMatches(messages("def f")) { 
      case Nil =>  
    }
    assertMatches(messages("def f[T] {}")) { 
      case Nil =>  
    }
    assertMatches(messages("def f(p: Any) {}")) { 
      case Nil =>  
    }
    assertMatches(messages("(p: Any) => ()")) { 
      case Nil =>  
    }
    assertMatches(messages("class C[T]")) { 
      case Nil =>  
    }
    assertMatches(messages("class C(p: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("class C(val p: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("class C(var p: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("(null, null) match { case p => }")) { 
      case Nil =>  
    }
  }
  
  def testDistinctNames {
    assertMatches(messages("class A; class B")) { 
      case Nil =>  
    }
    assertMatches(messages("case class A; case class B")) { 
      case Nil =>  
    }
    assertMatches(messages("trait A; trait B")) {
      case Nil =>  
    }
    assertMatches(messages("object A; object B")) { 
      case Nil =>  
    }
    assertMatches(messages("case object A; case object B")) { 
      case Nil =>  
    }
    assertMatches(messages("package a {}; package b {}")) { 
      case Nil =>  
    }
    assertMatches(messages("val a = null; val b = null")) { 
      case Nil =>  
    }
    assertMatches(messages("val a; val b")) { 
      case Nil =>  
    }
    assertMatches(messages("var a = null; var b = null")) { 
      case Nil =>  
    }
    assertMatches(messages("var a; var b")) { 
      case Nil =>  
    }
    assertMatches(messages("type A = Any; type B = Any")) { 
      case Nil =>  
    }
    assertMatches(messages("type A; type B")) { 
      case Nil =>  
    }
    assertMatches(messages("def a {}; def b {}")) { 
      case Nil =>  
    }
    assertMatches(messages("def a; def b")) { 
      case Nil =>  
    }
    assertMatches(messages("def f[A, B] {}")) { 
      case Nil =>  
    }
    assertMatches(messages("def f(a: Any, b: Any) {}")) { 
      case Nil =>  
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}")) { 
      case Nil =>  
    }
    assertMatches(messages("(a: Any, b: Any) => ()")) { 
      case Nil =>  
    }
    assertMatches(messages("class C[A, B]")) { 
      case Nil =>  
    }
    assertMatches(messages("class C(a: Any, b: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("class C(val a: Any, val b: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("class C(var a: Any, var b: Any)")) {
      case Nil =>  
    }
    assertMatches(messages("(null, null) match { case (a, b) => }")) { 
      case Nil =>  
    }
  }

  def testNameClash {
    assertMatches(messages("class C; class C")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("case class C; case class C")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("trait T; trait T")) {
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("object O; object O")) { 
      case Error("O", _) :: Error("O", _) :: Nil => 
    }
    assertMatches(messages("case object O; case object O")) { 
      case Error("O", _) :: Error("O", _) :: Nil => 
    }
    assertMatches(messages("package p {}; package p {}")) { 
      case Nil => 
    }
    assertMatches(messages("val v = null; val v = null")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("val v; val v")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("var v = null; var v = null")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("var v; var v")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("type A = Any; type A = Any")) { 
      case Error("A", _) :: Error("A", _) :: Nil => 
    }
    assertMatches(messages("type A; type A")) { 
      case Error("A", _) :: Error("A", _) :: Nil => 
    }
    assertMatches(messages("def f {}; def f {}")) { 
      case Error("f", _) :: Error("f", _) :: Nil => 
    }
    assertMatches(messages("def f; def f")) { 
      case Error("f", _) :: Error("f", _) :: Nil => 
    }
    assertMatches(messages("def f[T, T] {}")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("def f(p: Any, p: Any) {}")) { 
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("def f(p: Any)(p: Any) {}")) { 
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("(p: Any, p: Any) => ()")) { 
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("class C[T, T]")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("class C(p: Any, p: Any)")) {
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("class C(val p: Any, val p: Any)")) {
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("class C(var p: Any, var p: Any)")) {
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
    assertMatches(messages("(null, null) match { case (p, p) => }")) { 
      case Error("p", _) :: Error("p", _) :: Nil => 
    }
  }
  
  // TODO implement processing of distributed package declarations
//  def testNameClashInPackage {
//    assertMatches(messages("package p { class C }; package p { class C }")) { 
//      case Error("C", _) :: Error("C", _) :: Nil => 
//    }
//  }

  def testThreeClashedNames {
    assertMatches(messages("class C; class C; class C")) {
      case Error("C", _) :: Error("C", _) :: Error("C", _) :: Nil =>
    }
    assertMatches(messages("object O; object O; object O")) {
      case Error("O", _) :: Error("O", _) :: Error("O", _) :: Nil =>
    }
  }
  
  def testGroups {
    assertMatches(messages("def f(a: Any, a: Any); def f(b: Any, b: Any)")) { 
      case Error("f", _) :: Error("f", _) :: 
              Error("a", _) :: Error("a", _) :: 
              Error("b", _) :: Error("b", _) :: Nil =>  
    }
  }
  
   def testScope {
    assertMatches(messages("{ class C; class C}")) { 
      case Error("C", _) :: Error("C", _) :: Nil =>
    }
    assertMatches(messages("class X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("case class X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("trait X { class C; class C }")) {
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("object X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("case object X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("package X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }    
    assertMatches(messages("def X { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("val X = { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("var X = { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("for(x <- Nil) { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }    
    assertMatches(messages("if(true) { class C; class C } else { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Error("C", _) :: Error("C", _) :: Nil => 
    }    
    assertMatches(messages("while(true) { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }    
    assertMatches(messages("do { class C; class C } while(true)")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }    
    assertMatches(messages("try { class C; class C } catch { case _ => } finally { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Error("C", _) :: Error("C", _) :: Nil =>
    }
    assertMatches(messages("new { class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil =>
    }
    assertMatches(messages("null match { case _ => class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
    assertMatches(messages("val f: (Any => Unit) = { case _ => class C; class C }")) { 
      case Error("C", _) :: Error("C", _) :: Nil => 
    }
  }
  
  def testScopeBoundary {
    assertMatches(messages("class C; { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; class X { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; case class X { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; trait X { class C }")) {
      case Nil => 
    }
    assertMatches(messages("class C; object X { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; case object X { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; package X { class C }")) { 
      case Nil => 
    }    
    assertMatches(messages("class C; def X { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; val X = { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; var X = { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; for(x <- Nil) { class C }")) { 
      case Nil => 
    }    
    assertMatches(messages("class C; if(true) { class C } else { class C }")) { 
      case Nil => 
    }    
    assertMatches(messages("class C; while(true) { class C }")) { 
      case Nil => 
    }    
    assertMatches(messages("class C; do { class C } while(true)")) { 
      case Nil => 
    }    
    assertMatches(messages("class C; try { class C } catch { case _ => } finally { class C }")) { 
      case Nil => 
    }
    assertMatches(messages("class C; new { class C }")) { 
      case Nil => 
    }
    
    assertMatches(messages("class C; class X[C]")) { 
      case Nil => 
    }
    assertMatches(messages("object C; class X(O: Any)")) { 
      case Nil => 
    }
    assertMatches(messages("class C; def X[C] {}")) { 
      case Nil => 
    }
    assertMatches(messages("object O; def X(O: Any) {}")) { 
      case Nil => 
    }
    assertMatches(messages("null match { case v =>; case v => }")) { 
      case Nil => 
    }
    assertMatches(messages("val f: (Any => Unit) = { case v =>; case v => }")) { 
      case Nil => 
    }
  }
  
  def testNestedScopes {
    assertMatches(messages("class C; { class C; { class C } }")) { 
      case Nil => 
    }
    assertMatches(messages("class X; { class X; { class C; class C } }")) { 
      case Error("C", _) :: Error("C", _) :: Nil =>
    }
  }
  
  def testSameLeveScopeBoundary {
    assertMatches(messages("{ class C }; { class C }")) { 
      case Nil => 
    }
  }
  
  def testTypesClash {
    assertMatches(messages("class T; trait T")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("class T; type T = Any")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("class T; type T")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
    assertMatches(messages("class T; case class T")) { 
      case Error("T", _) :: Error("T", _) :: Nil => 
    }
  }
  
  def testTermsClash {
    assertMatches(messages("def v {}; def v")) {
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("def v {}; val v = null")) {
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("def v {}; val v")) {
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("def v {}; var v = null")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    } 
    assertMatches(messages("def v {}; var v")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    } 
    assertMatches(messages("def v {}; object v")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    } 
    assertMatches(messages("def v {}; case class v")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
  }
   
  def testTypeAndTerms {
    assertMatches(messages("class x; val x = null")) {
      case Nil => 
    }
    assertMatches(messages("class x; val x")) {
      case Nil => 
    }
    assertMatches(messages("class x; var x = null")) {
      case Nil => 
    }
    assertMatches(messages("class x; var x")) {
      case Nil => 
    }
    assertMatches(messages("class x; def x {}")) {
      case Nil => 
    }
    assertMatches(messages("class x; def x")) {
      case Nil => 
    }
    assertMatches(messages("class x; object x")) {
      case Nil => 
    }
    assertMatches(messages("class x; case object x")) {
      case Nil => 
    }
  }
  
  def testTypesOrTermsClash {
    assertMatches(messages("class X; class X; object X")) {
      case Error("X", _) :: Error("X", _) :: Nil => 
    }
    assertMatches(messages("object X; object X; class X")) {
      case Error("X", _) :: Error("X", _) :: Nil => 
    }
  }
  
  def testCaseClassCompanion {
    assertMatches(messages("case class X; object X")) { 
      case Nil => 
    } 
    assertMatches(messages("case class X; case object X")) { 
      case Nil => 
    }
    assertMatches(messages("case class v; def v {}")) { 
      case Error("v", _) :: Error("v", _) :: Nil => 
    }
    assertMatches(messages("case class X; object X; def X {}")) { 
      case Error("X", _) :: Error("X", _) :: Error("X", _) :: Nil =>
    }
    assertMatches(messages("case class X; object X; object X")) { 
      case Error("X", _) :: Error("X", _) :: Nil =>
    }
    assertMatches(messages("case class X; case class X; object X")) { 
      case Error("X", _) :: Error("X", _) :: Nil =>
    }
  }

  def testFunctionSignature() {
    assertMatches(messages("def f() {}; def f(p: Any) {}")) {
      case Nil =>  
    }
    assertMatches(messages("def a(p: Any) {}; def b(p: Any) {}")) {
      case Nil =>  
    }
    assertMatches(messages("def f(p: AnyRef) {}; def f(p: AnyVal) {}")) {
      case Nil =>  
    }
    assertMatches(messages("def f(a: Any) {}; def f(a: Any, b: Any) {}")) {
      case Nil =>  
    }
    assertMatches(messages("def f(a: Any) {}; def f(a: Any)(b: Any) {}")) {
      case Nil =>  
    }
    assertMatches(messages("def f(a: Any)(b: AnyRef) {}; def f(a: Any)(b: AnyVal) {}")) {
      case Nil =>  
    }
     assertMatches(messages("def f(a: Any, b: Any) {}; def f(a: Any)(b: Any) {}")) {
      case Nil =>  
    }
    
    assertMatches(messages("def f {}; def f {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>
    }
    assertMatches(messages("def f() {}; def f() {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>
    }
    assertMatches(messages("def f {}; def f() {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>
    }
    assertMatches(messages("def f(p: Any) {}; def f(p: Any) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>  
    }
    assertMatches(messages("def f(a: Any) {}; def f(b: Any) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>  
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>  
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>  
    }
  }
  
  // TODO implement function signatures comparison based on types (not on plain text representations)
//  def testFunctionSignatureTypeConformanceAndErasure {
//    assertMatches(messages("type Foo = Any; def f(p: Any) {}; def f(p: Foo) {}")) {
//      case Error("f", _) :: Error("f", _) :: Nil =>  
//    }
//    assertMatches(messages("def f(p: Any) {}; def f(p: AnyVal) {}")) {
//      case Error("f", _) :: Error("f", _) :: Nil =>  
//    }
//  }
  
  def testMessages {
    assertMatches(messages("class Foo; class Foo")) {
      case Error(_, m) :: _ if m.contains("Foo") && m.contains("already defined") =>  
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}")) {
      case Error(_, m) :: _ if m.contains("f(Any, Any)") =>  
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}")) {
      case Error(_, m) :: _ if m.contains("f(Any)(Any)") =>  
    }
  }
  
  protected def messages(@Language("Scala") code: String): List[Message] = {
    val psi = code.parse
    val annotator = new ScopeAnnotator() {}
    val mock = new AnnotatorHolderMock

    psi.depthFirst.foreach {
      annotator.annotateScope(_, mock)  
    }
    
    mock.annotations
  }
}