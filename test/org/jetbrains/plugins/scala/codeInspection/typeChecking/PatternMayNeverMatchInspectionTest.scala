package org.jetbrains.plugins.scala.codeInspection.typeChecking

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 21.12.15.
  */
//class PatternMayNeverMatchInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
//  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[PatternMayNeverMatchInspection]
//
//  override protected def annotation: String = PatternMayNeverMatchInspection.inspectionName
//
//  def testSCL9668(): Unit = {
//    val code =
//      s"""
//        |object Moo {
//        |  (1, 2) match {
//        |    case ${START}ScFunctionType(_, _)$END =>
//        |    case _ =>
//        |  }
//        |}
//        |class ScFunctionType(a: Foo, b: Seq[Foo])
//        |
//        |object ScFunctionType {
//        |  def unapply(f: Foo): Option[(Foo, Seq[Foo])] = ???
//        |}
//        |trait Foo
//      """.stripMargin
//    checkTextHasError(code)
//  }
//}

