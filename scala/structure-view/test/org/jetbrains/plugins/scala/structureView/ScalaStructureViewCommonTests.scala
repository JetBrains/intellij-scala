package org.jetbrains.plugins.scala.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.{IconManager, PlatformIcons}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.icons.Icons.*
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.*
import org.jetbrains.plugins.scala.structureView.filter.ScalaPublicElementsFilter
import org.jetbrains.plugins.scala.structureView.grouper.ScalaSuperTypesGrouper
import org.jetbrains.plugins.scala.structureView.sorter.{ScalaAlphaSorter, ScalaVisibilitySorter}

abstract class ScalaStructureViewCommonTests extends ScalaStructureViewTestBase {

  protected def getPlatformIcon(id: PlatformIcons) = IconManager.getInstance.getPlatformIcon(id)

  protected lazy val BlockIcon = getPlatformIcon(PlatformIcons.ClassInitializer)
  protected lazy val MethodIcon = getPlatformIcon(PlatformIcons.Method)
  protected lazy val AbstractMethodIcon = getPlatformIcon(PlatformIcons.AbstractMethod)
  protected lazy val ProtectedIcon = getPlatformIcon(PlatformIcons.Protected)
  protected lazy val PrivateIcon = getPlatformIcon(PlatformIcons.Private)

  protected val EmptyBlockNodeText = ""

  def testEmptyFile(): Unit = {
    check("")
  }

  def testVariable(): Unit = {
    check("""
          var v: Int = 1
          """,
      Node(VAR, "v: Int"))
  }

  def testMultipleVariables(): Unit = {
    check("""
          var v1, v2: Int = 1
          """,
      Node(VAR, "v1: Int"),
      Node(VAR, "v2: Int"))
  }

  //noinspection NotImplementedCode
  def testMultipleVariables_InPattern(): Unit = {
    check(
      """val (v1, v2, v3, (v4, v5)) = ???
        |""".stripMargin,
      Node(VAL, "v1"),
      Node(VAL, "v2"),
      Node(VAL, "v3"),
      Node(VAL, "v4"),
      Node(VAL, "v5")
    )
  }

  def testMemberVariable(): Unit = {
    check("""
          class Container {
            var v: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(FIELD_VAR, "v: Int")))
  }

  def testAbstractMemberVariable(): Unit = {
    check("""
          trait Container {
            var v: Int
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_FIELD_VAR, "v: Int")))
  }

  def testFinalMemberVariable(): Unit = {
    check("""
          trait Container {
            final var v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(FIELD_VAR, AllIcons.Nodes.FinalMark), "v: Int")))
  }

  def testMemberVariableVisibility(): Unit = {
    check("""
          trait Container {
             private var v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(FIELD_VAR, PrivateIcon, "v: Int")))
  }

  //  def testVariableTypeInference(): Unit = {
  //    check("""
  //          var v = 1
  //          """,
  //      Node(VAR, "v: Int"))
  //  }

  def testDeprecation(): Unit = check(
    """
       object Container {
         @deprecated var v1: Int = 1
         @deprecated val v2: Int = 1
         @deprecated val v3, v4 = 1
         @deprecated type A = Int
         @deprecated def m: Int = 1
         @deprecated class C
         class C2 @deprecated() (i: Int, @deprecated val s: String) {
           @deprecated def this() = this(2, "")
           def this(@deprecated i: Int) = this(i, "")
         }
         @deprecated trait T
         @deprecated object O
       }
    """,
    Node(OBJECT, "Container",
      Node(FIELD_VAR, "v1: Int", DeprecatedAttributesKey),
      Node(FIELD_VAL, "v2: Int", DeprecatedAttributesKey),
      Node(FIELD_VAL, "v3", DeprecatedAttributesKey),
      Node(FIELD_VAL, "v4", DeprecatedAttributesKey),
      Node(TYPE_ALIAS, "A", DeprecatedAttributesKey),
      Node(MethodIcon, "m: Int", DeprecatedAttributesKey),
      Node(CLASS, "C", DeprecatedAttributesKey),
      Node(CLASS, "C2(Int, String)",
        Node(FIELD_VAL, "s: String", DeprecatedAttributesKey),
        Node(MethodIcon, "this()", DeprecatedAttributesKey),
        Node(MethodIcon, "this(Int)"),
      ),
      Node(TRAIT, "T", DeprecatedAttributesKey),
      Node(OBJECT, "O", DeprecatedAttributesKey)
    )
  )

  def testImplicitParams(): Unit = check(
    """
       object Container {
         class C1(i: Int)(implicit s: String)
         class C2(i: Int) {
           def this()(implicit s: String) = this(s.length)
         }
         def m1(implicit i: Int, s: String): Unit = {}
         def m2[A, B, C, D](a: A)(implicit b: B, c: C): D = ???
       }
    """,
    Node(OBJECT, "Container",
      Node(CLASS, "C1(Int)(?=> String)"),
      Node(CLASS, "C2(Int)",
        Node(MethodIcon, "this()(?=> String)")
      ),
      Node(MethodIcon, "m1(?=> Int, String): Unit"),
      Node(MethodIcon, "m2[A, B, C, D](A)(?=> B, C): D"),
    )
  )

  def testDefaultParams(): Unit = check(
    """
       object Container {
         def someMethod(x: Int, y: => Int, z: Int = 5)(a: => Int = 2): Int = x + 2 * y + z - a

         class C1(i: Int = 1)(val b: Boolean = true)(implicit s: String = "default")
         case class C2(i: Int = 0) {
           def this()(s: String = "s") = this(s.length)
         }
       }
    """,
    Node(OBJECT, "Container",
      Node(MethodIcon, "someMethod(Int, => Int, Int = …)(=> Int = …): Int"),
      Node(CLASS, "C1(Int = …)(Boolean = …)(?=> String = …)",
        Node(FIELD_VAL, "b: Boolean")
      ),
      Node(CLASS, "C2(Int = …)",
        Node(FIELD_VAL, "i: Int"),
        Node(MethodIcon, "this()(String = …)")
      )
    )
  )

  def testValue(): Unit = {
    check("""
          val v: Int = 1
          """,
      Node(VAL, "v: Int"))
  }

  def testMultipleValues(): Unit = {
    check("""
          val v1, v2: Int = 1
          """,
      Node(VAL, "v1: Int"),
      Node(VAL, "v2: Int"))
  }

  //noinspection NotImplementedCode
  def testMultipleValues_InPattern(): Unit = {
    check(
      """val (v1, v2, v3, (v4, v5)) = ???
        |""".stripMargin,
      Node(VAL, "v1"),
      Node(VAL, "v2"),
      Node(VAL, "v3"),
      Node(VAL, "v4"),
      Node(VAL, "v5")
    )
  }

  def testMemberValue(): Unit = {
    check("""
          class Container {
            val v: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(FIELD_VAL, "v: Int")))
  }

  def testAbstractMemberValue(): Unit = {
    check("""
          trait Container {
            val v: Int
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_FIELD_VAL, "v: Int")))
  }

  def testFinalMemberValue(): Unit = {
    check("""
          trait Container {
            final val v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(FIELD_VAL, FinalMark), "v: Int")))
  }

  def testMemberValueVisibility(): Unit = {
    check("""
          trait Container {
            private val v: Int = 1
          }
          """,
      Node(TRAIT, "Container",
        Node(FIELD_VAL, PrivateIcon, "v: Int")))
  }

  //  def testValueTypeInference(): Unit = {
  //    check("""
  //          val v = 1
  //          """,
  //      Node(VAL, "v: Int"))
  //  }

  def testTypeAlias(): Unit = {
    check("""
          type A = Int
          """,
      Node(TYPE_ALIAS, "A"))
  }

  def testMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(TYPE_ALIAS, "A")))
  }

  def testAbstractMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            type A
          }
          """,
      Node(TRAIT, "Container",
        Node(ABSTRACT_TYPE_ALIAS, "A")))
  }

  def testFinalMemberTypeAlias(): Unit = {
    check("""
          trait Container {
            final type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(layered(TYPE_ALIAS, FinalMark), "A")))
  }

  def testMemberTypeAliasVisibility(): Unit = {
    check("""
          trait Container {
            private type A = Int
          }
          """,
      Node(TRAIT, "Container",
        Node(TYPE_ALIAS, PrivateIcon, "A")))
  }

  def testFunction(): Unit = {
    check("""
          def m: Int = 1
          """,
      Node(FUNCTION, "m: Int"))
  }

  def testFunctionVisibility(): Unit = {
    check("""
          private def m: Int = 1
          """,
      Node(FUNCTION, PrivateIcon, "m: Int"))
  }

  //  def testFunctionTypeInference(): Unit = {
  //    check("""
  //          def m(p: Any) = 1
  //          """,
  //      Node(FUNCTION, "m(Any): Int"))
  //  }

  def testMethod(): Unit = {
    check("""
          class Container {
            def m: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(MethodIcon, "m: Int")))
  }

  def testAbstractMethod(): Unit = {
    check("""
          class Container {
            def m: Int
          }
          """,
      Node(CLASS, "Container",
        Node(AbstractMethodIcon, "m: Int")))
  }

  def testFinalMethod(): Unit = {
    check("""
          class Container {
            final def m: Int = 1
          }
          """,
      Node(CLASS, "Container",
        Node(layered(MethodIcon, FinalMark), "m: Int")))
  }

  def testTypeParametersInFunction(): Unit = {
    check("""
          def m[A, B]: Int = 1
          """,
      Node(FUNCTION, "m[A, B]: Int"))
  }

  //  def testFunctionTypeParameterPresentation(): Unit = {
  //    check("""
  //          def m[T <: Any]: Int = 1
  //          """,
  //      Node(FUNCTION, "m[T]: Int"))
  //  }

  def testParameterListInFunction(): Unit = {
    check("""
          def m(): Int = 1
          """,
      Node(FUNCTION, "m(): Int"))
  }

  def testMultipleParametersInFunction(): Unit = {
    check("""
          def m(p1: Float, p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float, Double): Int"))
  }

  def testMultipleParameterListsInFunction(): Unit = {
    check("""
          def m(p1: Float)(p2: Double): Int = 1
          """,
      Node(FUNCTION, "m(Float)(Double): Int"))
  }

  def testObject(): Unit = {
    check("""
          object O
          """,
      Node(OBJECT, "O"))
  }

  def testObjectVisibility(): Unit = {
    check("""
          private object O
          """,
      Node(OBJECT, PrivateIcon, "O"))
  }

  def testPackageObject(): Unit = {
    check("""
          package object O
          """,
      Node(PACKAGE_OBJECT, "O"))
  }

  def testClass(): Unit = {
    check("""
          class C
          """,
      Node(CLASS, "C"))
  }

  def testAbstractClass(): Unit = {
    check("""
          abstract class C
          """,
      Node(ABSTRACT_CLASS, "C"))
  }

  def testFinalClass(): Unit = {
    check("""
          final class C
          """,
      Node(layered(CLASS, FinalMark), "C"))
  }

  def testClassVisibility(): Unit = {
    check("""
          private class C
          """,
      Node(CLASS, PrivateIcon, "C"))
  }

  def testClassTypeParameters(): Unit = {
    check("""
          class C[A, B]
          """,
      Node(CLASS, "C[A, B]"))
  }

  def testTrait(): Unit = {
    check("""
          trait T
          """,
      Node(TRAIT, "T"))
  }

  def testTraitVisibility(): Unit = {
    check("""
          private trait T
          """,
      Node(TRAIT, PrivateIcon, "T"))
  }

  def testTraitTypeParameters(): Unit = {
    check("""
          trait T[A, B]
          """,
      Node(TRAIT, "T[A, B]"))
  }

  def testTypeDefinitionTypeParameterPresentation(): Unit = {
    check("""
          class C[T <: Any]
          """,
      Node(CLASS, "C[T]"))
  }

  def testParameterListInPrimaryConstructor(): Unit = {
    check("""
          class C()
          """,
      Node(CLASS, "C()"))
  }

  def testMultipleParametersInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float, p2: Double)
          """,
      Node(CLASS, "C(Float, Double)"))
  }

  def testMultipleParameterListsInPrimaryConstructor(): Unit = {
    check("""
          class C(p1: Float)(p2: Double)
          """,
      Node(CLASS, "C(Float)(Double)"))
  }

  def testVariablesInPrimaryConstructor(): Unit = {
    check("""
          class C(var p1: Float, var p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAR, "p1: Float"),
        Node(FIELD_VAR, "p2: Double")))
  }

  def testFinalVariableInPrimaryConstructor(): Unit = {
    check("""
          class C(final var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAR, FinalMark), "p: Int")))
  }

  def testVariableVisibilityInPrimaryConstructor(): Unit = {
    check("""
          class C(private var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAR, PrivateIcon, "p: Int")))
  }

  def testValuesInPrimaryConstructor(): Unit = {
    check("""
          class C(val p1: Float, val p2: Double)
          """,
      Node(
        CLASS,
        "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")
      ))
  }

  def testFinalValueInPrimaryConstructor(): Unit = {
    check("""
          class C(final val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAL, FinalMark), "p: Int")))
  }

  def testValueVisibilityInPrimaryConstructor(): Unit = {
    check("""
          class C(private val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAL, PrivateIcon, "p: Int")))
  }

  def testMultipleParameterListsWithMembersInPrimaryConstructor(): Unit = {
    check("""
          class C(val p1: Float)(val p2: Double)
          """,
      Node(
        CLASS, "C(Float)(Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")
      )
    )
  }

  def testParametersInCaseClass(): Unit = {
    check("""
          case class C(p1: Float, p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")))
  }

  //noinspection CaseClassParam
  def testValuesInCaseClass(): Unit = {
    check("""
          case class C(val p1: Float, val p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAL, "p1: Float"),
        Node(FIELD_VAL, "p2: Double")))
  }

  def testFinalValueParameterInCaseClass(): Unit = {
    check("""
          case class C(final val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAL, FinalMark), "p: Int")))
  }

  def testPrivateValueParameterInCaseClass(): Unit = {
    check("""
          case class C(private val p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAL, PrivateIcon, "p: Int")))
  }

  def testVariablesInCaseClass(): Unit = {
    check("""
          case class C(var p1: Float, var p2: Double)
          """,
      Node(CLASS, "C(Float, Double)",
        Node(FIELD_VAR, "p1: Float"),
        Node(FIELD_VAR, "p2: Double")))
  }

  def testFinalVariableParameterInCaseClass(): Unit = {
    check("""
          case class C(final var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(layered(FIELD_VAR, FinalMark), "p: Int")))
  }

  def testPrivateVariableParameterInCaseClass(): Unit = {
    check("""
          case class C(private var p: Int)
          """,
      Node(CLASS, "C(Int)",
        Node(FIELD_VAR, PrivateIcon, "p: Int")))
  }

  def testAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this() { this() }
          }
          """,
      Node(CLASS, "C",
        Node(MethodIcon, "this()")))
  }

  def testParameterInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p: Int) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(MethodIcon, "this(Int)")))
  }

  def testMultipleParametersInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float, p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(MethodIcon, "this(Float, Double)")))
  }

  def testMultipleParameterListsInAuxiliaryConstructor(): Unit = {
    check("""
          class C {
            def this(p1: Float)(p2: Double) { this() }
          }
          """,
      Node(CLASS, "C",
        Node(MethodIcon, "this(Float)(Double)")))
  }

  def testBlock(): Unit = {
    check("""
          {}
          """,
      new Node(BlockIcon, ""))
  }

  def testInsideClass(): Unit = {
    check("""
          class Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(CLASS, "Container",
        new Node(BlockIcon, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(MethodIcon, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideTrait(): Unit = {
    check("""
          trait Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(TRAIT, "Container",
        new Node(BlockIcon, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(MethodIcon, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideObject(): Unit = {
    check("""
          object Container {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(OBJECT, "Container",
        new Node(BlockIcon, ""),
        Node(FIELD_VAR, "v1: Int"),
        Node(FIELD_VAL, "v2: Int"),
        Node(TYPE_ALIAS, "A"),
        Node(MethodIcon, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideBlock(): Unit = {
    check("""
          {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      new Node(BlockIcon, "",
        new Node(BlockIcon, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideVariable(): Unit = {
    check("""
          var v: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(VAR, "v: Int",
        new Node(BlockIcon, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideValue(): Unit = {
    check("""
          val v: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(VAL, "v: Int",
        new Node(BlockIcon, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testInsideMethod(): Unit = {
    check("""
          def m: Int = {
            {}
            var v1: Int = 1
            val v2: Int = 1
            type A = Int
            def m: Int = 1
            class C
            trait T
            object O
          }
          """,
      Node(FUNCTION, "m: Int",
        new Node(BlockIcon, ""),
        Node(FUNCTION, "m: Int"),
        Node(CLASS, "C"),
        Node(TRAIT, "T"),
        Node(OBJECT, "O")))
  }

  def testClassAndObject(): Unit = {
    check("""
          class C
          object C
          """,
      Node(CLASS, "C"),
      Node(OBJECT, "C"))
  }

  //noinspection ScalaUnnecessarySemicolon
  def testOrdering(): Unit = {
    check("""
          {}
          var r1: Int = 1
          val l1: Int = 1
          type A1 = Int
          def m1: Int = 1
          class C1
          trait T1
          object O1
          object O2
          trait T2
          class C2
          def m2: Int = 1
          type A2 = Int
          val l2: Int = 1
          var r2: Int = 1;
          {}
          """,
      new Node(BlockIcon, ""),
      Node(VAR, "r1: Int"),
      Node(VAL, "l1: Int"),
      Node(TYPE_ALIAS, "A1"),
      Node(FUNCTION, "m1: Int"),
      Node(CLASS, "C1"),
      Node(TRAIT, "T1"),
      Node(OBJECT, "O1"),
      Node(OBJECT, "O2"),
      Node(TRAIT, "T2"),
      Node(CLASS, "C2"),
      Node(FUNCTION, "m2: Int"),
      Node(TYPE_ALIAS, "A2"),
      Node(VAL, "l2: Int"),
      Node(VAR, "r2: Int"),
      new Node(BlockIcon, ""))
  }

  def testAnonymousClasses(): Unit = {
    val code =
      """class ScalaStructureTest {
        |
        |  //not an anonymous class
        |  new Foo
        |  new Foo()
        |
        |  //inside primary constructor body
        |  new Foo() {}
        |  {
        |    new Foo() {}
        |  }
        |
        |  //inside function body without block
        |  def foo1(): AnyRef =
        |    new Foo() {}
        |
        |  def foo2(): Unit = {
        |    //inside function body
        |    new Foo() {}
        |
        |    {
        |      new Foo() {}
        |    }
        |
        |    //inside arguments
        |    new Foo(new Foo() {}) {}
        |    println(new Foo() {})
        |
        |    //inside arguments, multiple arguments list
        |    new Foo("42")(new Foo() {}) {}
        |
        |    def bar(x: AnyRef)(y: AnyRef): Unit = ()
        |
        |    bar(new Foo() {})("42")
        |    bar("42")(new Foo() {})
        |
        |    //inside inner class
        |    class Inner {
        |      new Foo() {}
        |      new Foo() {}
        |    }
        |
        |    //inside anonymous class
        |    new AnyRef {
        |      new Foo() {}
        |    }
        |  }
        |}
        |""".stripMargin

    val expectedStructureWithAnonymousDisabled =
      s"""-ScalaStructureTest.scala
         | -ScalaStructureTest
         |  $EmptyBlockNodeText
         |  foo1(): AnyRef
         |  -foo2(): Unit
         |   $EmptyBlockNodeText
         |   bar(AnyRef)(AnyRef): Unit
         |   Inner
         |""".stripMargin.trim

    val expectedStructureWithAnonymousEnabled =
      s"""-ScalaStructureTest.scala
         | -ScalaStructureTest
         |  $$1
         |  -$EmptyBlockNodeText
         |   $$2
         |  -foo1(): AnyRef
         |   $$3
         |  -foo2(): Unit
         |   $$4
         |   -$EmptyBlockNodeText
         |    $$5
         |   $$6
         |   $$7
         |   $$8
         |   $$9
         |   $$10
         |   bar(AnyRef)(AnyRef): Unit
         |   $$11
         |   $$12
         |   -Inner
         |    $$1
         |    $$2
         |   -$$13
         |    $$14
         |""".stripMargin.trim

    myFixture.configureByText("ScalaStructureTest.scala", code)

    //NOTE: our common test code from `ScalaStructureViewTestBase` can't test
    // nodes coming from com.intellij.ide.util.FileStructureNodeProvider
    //In IntelliJ tests they test it using this fixture method
    myFixture.testStructureView((svc: StructureViewComponent) => {
      val tree = svc.getTree

      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree, expectedStructureWithAnonymousDisabled)

      svc.setActionActive(ScalaAnonymousClassesNodeProvider.ID, true)

      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree, expectedStructureWithAnonymousEnabled)
    })
  }

  def testAnonymousClass_ShowWithInheritedMembers(): Unit = {
    val code =
      """class MyBaseClass {
        |  def fooFromBaseClass: String = ???
        |}
        |
        |object example {
        |  new MyBaseClass() {
        |    def foo: String = ???
        |  }
        |}
        |""".stripMargin

    val expectedStructureWithAnonymousEnabled =
      s"""-ScalaStructureTest.scala
         | -MyBaseClass
         |  fooFromBaseClass: String
         |  getClass(): Class[_]
         |  wait(Long, Int): Unit
         |  wait(Long): Unit
         |  wait(): Unit
         |  hashCode(): Int
         |  equals(Object): Boolean
         |  notifyAll(): Unit
         |  clone(): Object
         |  toString(): String
         |  finalize(): Unit
         |  notify(): Unit
         | -example
         |  getClass(): Class[_]
         |  wait(Long, Int): Unit
         |  wait(Long): Unit
         |  wait(): Unit
         |  hashCode(): Int
         |  equals(Object): Boolean
         |  notifyAll(): Unit
         |  clone(): Object
         |  toString(): String
         |  finalize(): Unit
         |  notify(): Unit
         |  -$$1
         |   foo: String
         |   getClass(): Class[_]
         |   wait(Long, Int): Unit
         |   wait(Long): Unit
         |   wait(): Unit
         |   hashCode(): Int
         |   equals(Object): Boolean
         |   notifyAll(): Unit
         |   clone(): Object
         |   toString(): String
         |   finalize(): Unit
         |   fooFromBaseClass: String
         |   notify(): Unit
         | """.stripMargin.trim

    myFixture.configureByText("ScalaStructureTest.scala", code)

    //NOTE: our common test code from `ScalaStructureViewTestBase` can't test
    // nodes coming from com.intellij.ide.util.FileStructureNodeProvider
    //In IntelliJ tests they test it using this fixture method
    myFixture.testStructureView((svc: StructureViewComponent) => {
      val tree = svc.getTree

      svc.setActionActive(ScalaAnonymousClassesNodeProvider.ID, true)
      svc.setActionActive(ScalaInheritedMembersNodeProvider.ID, true)

      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree, expectedStructureWithAnonymousEnabled)
    })
  }

  def testInheritedMembers(): Unit = {
    @Language("Scala")
    val baseClass =
      """package tests
        |
        |class Base(val param1: Int, var param2: String) {
        |  class InnerClass
        |  object InnerObject
        |  trait InnerTrait
        |
        |  protected class ProtectedInnerClass
        |  protected[this] class ProtectedThisInnerClass // it is available inside derived class
        |  protected[tests] class ProtectedScopeInnerClass
        |
        |  private class PrivateInnerClass // unavailable
        |  private[this] class PrivateThisInnerClass // unavailable
        |  private[tests] class PrivateScopeInnerClass // available inside this package
        |
        |  def m1(): Unit = {}
        |  def m2(i: Int)(implicit s: String): Unit = ()
        |
        |  protected def protectedM(): Unit = {}
        |  protected[this] def protectedThisM(): Unit = {}
        |  protected[tests] def protectedScopeM(): Unit = {}
        |
        |  private def privateM(): Unit = {}
        |  private[this] def privateThisM(): Unit = {}
        |  private[tests] def privateScopeM(): Unit = {}
        |
        |  val v1: Int = 1
        |  var (v2, v3) = (true, "v3")
        |
        |  protected val protectedV: Int = 2
        |  protected[this] val protectedThisV: Int = 3
        |  protected[tests] val protectedScopeV: Int = 4
        |
        |  private val privateV: Int = 5
        |  private[this] val privateThisV: Int = 6
        |  private[tests] val privateScopeV: Int = 7
        |
        |  type IntAlias = Int
        |  type ListAlias[T] = List[T]
        |
        |  protected type ProtectedStringAlias = String
        |  protected[this] type ProtectedThisStringAlias = String
        |  protected[tests] type ProtectedScopeStringAlias = String
        |
        |  private type PrivateStringAlias = String
        |  private[this] type PrivateThisStringAlias = String
        |  private[tests] type PrivateScopeStringAlias = String
        |}
        |""".stripMargin
    myFixture.addFileToProject("tests/Base.scala", baseClass)

    @Language("Scala")
    val derivedClass =
      """package tests
        |
        |class Derived extends Base(0, "")
        |""".stripMargin
    configureFromFileText(derivedClass)

    def inheritedFromObject(indent: Int = 3) =
      """clone(): Object
        |""".stripMargin +
      """equals(Object): Boolean
        |finalize(): Unit
        |getClass(): Class[_]
        |hashCode(): Int
        |notify(): Unit
        |notifyAll(): Unit
        |toString(): String
        |wait(): Unit
        |wait(Long): Unit
        |wait(Long, Int): Unit""".stripMargin.linesIterator.map(" " * indent + _).mkString(System.lineSeparator())

    myFixture.testStructureView { svc =>
      svc.setActionActive(ScalaInheritedMembersNodeProvider.ID, true)
      svc.setActionActive(ScalaAlphaSorter.ID, true)
      PlatformTestUtil.expandAll(svc.getTree)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  clone(): Object
           |  equals(Object): Boolean
           |  finalize(): Unit
           |  getClass(): Class[_]
           |  hashCode(): Int
           |  -InnerClass
           |   ${inheritedFromObject()}
           |  -InnerObject
           |   ${inheritedFromObject()}
           |  -InnerTrait
           |   ${inheritedFromObject()}
           |  IntAlias
           |  ListAlias
           |  m1(): Unit
           |  m2(Int)(?=> String): Unit
           |  notify(): Unit
           |  notifyAll(): Unit
           |  param1: Int
           |  param2: String
           |  -PrivateScopeInnerClass
           |   ${inheritedFromObject()}
           |  privateScopeM(): Unit
           |  PrivateScopeStringAlias
           |  privateScopeV: Int
           |  -ProtectedInnerClass
           |   ${inheritedFromObject()}
           |  protectedM(): Unit
           |  -ProtectedScopeInnerClass
           |   ${inheritedFromObject()}
           |  protectedScopeM(): Unit
           |  ProtectedScopeStringAlias
           |  protectedScopeV: Int
           |  ProtectedStringAlias
           |  -ProtectedThisInnerClass
           |   ${inheritedFromObject()}
           |  protectedThisM(): Unit
           |  ProtectedThisStringAlias
           |  protectedThisV: Int
           |  protectedV: Int
           |  toString(): String
           |  v1: Int
           |  v2
           |  v3
           |  wait(): Unit
           |  wait(Long): Unit
           |  wait(Long, Int): Unit
           |""".stripMargin
      )

      svc.setActionActive(ScalaSuperTypesGrouper.ID, true)
      PlatformTestUtil.expandAll(svc.getTree)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  -Base
           |   IntAlias
           |   ListAlias
           |   m1(): Unit
           |   m2(Int)(?=> String): Unit
           |   param1: Int
           |   param2: String
           |   privateScopeM(): Unit
           |   PrivateScopeStringAlias
           |   privateScopeV: Int
           |   protectedM(): Unit
           |   protectedScopeM(): Unit
           |   ProtectedScopeStringAlias
           |   protectedScopeV: Int
           |   ProtectedStringAlias
           |   protectedThisM(): Unit
           |   ProtectedThisStringAlias
           |   protectedThisV: Int
           |   protectedV: Int
           |   v1: Int
           |   v2
           |   v3
           |  -InnerClass
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -InnerObject
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -InnerTrait
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -Object
           |   ${inheritedFromObject()}
           |  -PrivateScopeInnerClass
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -ProtectedInnerClass
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -ProtectedScopeInnerClass
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |  -ProtectedThisInnerClass
           |   -Object
           |    ${inheritedFromObject(indent = 4)}
           |""".stripMargin
      )
    }
  }

  def testInheritedMembersFromJava(): Unit = {
    @Language("java")
    val baseClass =
      """package tests;
        |
        |class Base {
        |  class InnerClass {}
        |  static class InnerStaticClass {}
        |  private class PrivateInnerClass {}
        |  private static class PrivateInnerStaticClass {}
        |
        |  void m1() {}
        |  int m2(String s) { return s.length(); }
        |  static void m3(boolean b) {}
        |  private void privateM() {}
        |  private static void privateStaticM(boolean b) {}
        |
        |  final int v1 = 1;
        |  private final int privateV = 1;
        |  boolean v2 = true;
        |  static String v3 = "v3";
        |  private static String privateStaticV = "...";
        |}
        |""".stripMargin
    myFixture.addFileToProject("tests/Base.java", baseClass)

    @Language("Scala")
    val derivedClass =
      """package tests
        |
        |class Derived extends Base
        |""".stripMargin
    configureFromFileText(derivedClass)

    myFixture.testStructureView { svc =>
      svc.setActionActive(ScalaInheritedMembersNodeProvider.ID, true)
      svc.setActionActive(ScalaAlphaSorter.ID, true)
      PlatformTestUtil.expandAll(svc.getTree)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  clone(): Object
           |  equals(Object): Boolean
           |  finalize(): Unit
           |  getClass(): Class[_]
           |  hashCode(): Int
           |  InnerClass
           |  InnerStaticClass
           |  m1(): Unit
           |  m2(String): Int
           |  notify(): Unit
           |  notifyAll(): Unit
           |  toString(): String
           |  v1: int = 1
           |  v2: boolean = true
           |  wait(): Unit
           |  wait(Long): Unit
           |  wait(Long, Int): Unit
           |""".stripMargin
      )

      svc.setActionActive(ScalaSuperTypesGrouper.ID, true)
      PlatformTestUtil.expandAll(svc.getTree)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  -Base
           |   InnerClass
           |   InnerStaticClass
           |   m1(): Unit
           |   m2(String): Int
           |   v1: int = 1
           |   v2: boolean = true
           |  -Object
           |   clone(): Object
           |   equals(Object): Boolean
           |   finalize(): Unit
           |   getClass(): Class[_]
           |   hashCode(): Int
           |   notify(): Unit
           |   notifyAll(): Unit
           |   toString(): String
           |   wait(): Unit
           |   wait(Long): Unit
           |   wait(Long, Int): Unit
           |""".stripMargin
      )
    }
  }

  def testOrderingByVisibility(): Unit = {
    myFixture.addFileToProject("tests/Base.scala", BaseInterfaceAndClass)
    configureFromFileText(DerivedClass)

    myFixture.testStructureView { svc =>
      svc.setActionActive(ScalaInheritedMembersNodeProvider.ID, true)
      PlatformTestUtil.expandAll(svc.getTree)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  f(): Unit
           |  g(): Unit
           |  x
           |  i
           |  setX(Int): Unit
           |  getX
           |  setJ(Int): Unit
           |  getJ: Int
           |  setY(Int): Unit
           |  getY
           |  setI(Int): Unit
           |  getI
           |""".stripMargin +
          // derived
          """  getClass(): Class[_]
            |  wait(Long, Int): Unit
            |  wait(Long): Unit
            |  wait(): Unit
            |  notifyAll(): Unit
            |  h(): Unit
            |  setZ(Int): Unit
            |  notify(): Unit
            |  getZ: Int
            |  hashCode(): Int
            |  equals(Object): Boolean
            |  clone(): Object
            |  toString: String
            |  finalize(): Unit
            |""".stripMargin
      )

      PlatformTestUtil.expandAll(svc.getTree)
      svc.setActionActive(ScalaVisibilitySorter.ID, true)
      PlatformTestUtil.assertTreeEqual(svc.getTree,
        s"""
           |-${getFile.name}
           | -Derived
           |  f(): Unit
           |  g(): Unit
           |  setX(Int): Unit
           |  getX
           |  setY(Int): Unit
           |  getY
           |  setI(Int): Unit
           |  getI
           |""".stripMargin +
          // derived public
          """  getClass(): Class[_]
            |  wait(Long, Int): Unit
            |  wait(Long): Unit
            |  wait(): Unit
            |  notifyAll(): Unit
            |  notify(): Unit
            |  hashCode(): Int
            |  equals(Object): Boolean
            |  toString: String
            |""".stripMargin +
          // protected
          """  setJ(Int): Unit
            |  getJ: Int
            |""".stripMargin +
          // derived protected
          """  h(): Unit
            |  getZ: Int
            |  clone(): Object
            |  finalize(): Unit
            |""".stripMargin +
          // private
          """  x
            |  i
            |""".stripMargin +
          // derived private with scope
          """  setZ(Int): Unit
            |""".stripMargin
      )
    }
  }

  def testHideNonPublic(): Unit = {
    myFixture.addFileToProject("tests/Base.scala", BaseInterfaceAndClass)
    configureFromFileText(DerivedClass)

    myFixture.testStructureView { svc =>
      svc.setActionActive(ScalaInheritedMembersNodeProvider.ID, true)
      svc.setActionActive(ScalaAlphaSorter.ID, true)
      svc.setActionActive(ScalaPublicElementsFilter.ID, true)

      val tree = svc.getTree
      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree,
        s"""
           |-${getFile.name}
           | -Derived
           |  equals(Object): Boolean
           |  f(): Unit
           |  g(): Unit
           |  getClass(): Class[_]
           |  getI
           |  getX
           |  getY
           |  hashCode(): Int
           |  notify(): Unit
           |  notifyAll(): Unit
           |  setI(Int): Unit
           |  setX(Int): Unit
           |  setY(Int): Unit
           |  toString: String
           |  wait(): Unit
           |  wait(Long): Unit
           |  wait(Long, Int): Unit
           |""".stripMargin
      )
    }
  }

  @Language("Scala")
  private val BaseInterfaceAndClass =
    """
      |package tests
      |
      |trait Interface {
      |  def g(): Unit
      |  protected def h(): Unit = {}
      |  def setI(i: Int): Unit
      |  def getI: Int
      |}
      |
      |class Base {
      |  def f(): Unit = {}
      |  def toString: String = null
      |  private[tests] def setZ(z: Int): Unit = {}
      |  def g(): Unit = {}
      |  protected def getZ: Int = 0
      |  def setX(x: Int): Unit = {}
      |  def getX: Int = 0
      |}
      |""".stripMargin

  @Language("Scala")
  private val DerivedClass =
    """
      |package tests
      |
      |class Derived extends Base with Interface {
      |  def f(): Unit = {}
      |  def g(): Unit = {}
      |  private[this] val x = 0
      |  private val i = 0
      |  def setX(x: Int): Unit = {}
      |  def getX = 0
      |  protected def setJ(j: Int): Unit = {}
      |  protected def getJ: Int = 0
      |  def setY(x: Int): Unit = {}
      |  def getY = 0
      |  def setI(i: Int): Unit = {}
      |  def getI = 0
      |}
      |""".stripMargin
}
