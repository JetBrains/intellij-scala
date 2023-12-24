package org.jetbrains.plugins.scala.structureView

import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.icons.Icons.*
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.jetbrains.plugins.scala.structureView.grouper.ScalaSuperTypesGrouper
import org.jetbrains.plugins.scala.structureView.sorter.ScalaAlphaSorter
import org.jetbrains.plugins.scala.{LatestScalaVersions, Scala3Language, ScalaVersion}

class Scala3StructureViewTest extends ScalaStructureViewCommonTests {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def scalaLanguage: com.intellij.lang.Language = Scala3Language.INSTANCE

  override protected def check(@org.intellij.lang.annotations.Language("Scala 3") code: String, nodes: Node*): Unit =
    super.check(code, nodes: _*)

  override protected def checkNavigationFromSource(@org.intellij.lang.annotations.Language("Scala 3") code: String, expectedNodes: Node*): Unit =
    super.checkNavigationFromSource(code, expectedNodes: _*)

  private lazy val EnumCaseIcon = ENUM

  private val TopLevelDefinitionsText =
    """class MyClass()
      |
      |case class MyCaseClass()
      |
      |abstract class MyAbstractClass()
      |
      |trait MyTrait
      |
      |object MyObject
      |
      |enum MyEnum:
      |  case X
      |
      |type MyTypeAlias[T] = (String, T)
      |
      |val myValue = 1
      |
      |var myVariable = 2
      |
      |def myFunction: String = ???
      |
      |extension (x: MyClass) def myExtension(y: String) = ???
      |""".stripMargin

  private lazy val TopLevelDefinitionsNodes: Seq[Node] =
    Seq(
      Node(CLASS, "MyClass()"),
      Node(CASE_CLASS, "MyCaseClass()"),
      Node(ABSTRACT_CLASS, "MyAbstractClass()"),
      Node(TRAIT, "MyTrait"),
      Node(OBJECT, "MyObject"),
      Node(ENUM, "MyEnum", Node(EnumCaseIcon, "X")),
      Node(TYPE_ALIAS, "MyTypeAlias"),
      Node(VAL, "myValue"),
      Node(VAR, "myVariable"),
      Node(FUNCTION, "myFunction: String"),
      Node(EXTENSION, "extension (MyClass)", Seq(
        Node(FUNCTION, "myExtension(String)")
      ): _*),
    )

  def testTopLevelDefinitions_InRootPackage(): Unit = {
    check(TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
  }

  def testTopLevelDefinitions_InPackage(): Unit = {
    check("package aaa.bbb.ccc\n" + TopLevelDefinitionsText, TopLevelDefinitionsNodes: _*)
  }

  def testAnonymousClasses_InsideValAndVarBody(): Unit = {
    val code =
      """object MyClass {
        |  //`val`, fields
        |  val value1: Runnable = new Runnable() { override def run(): Unit = () }
        |  val value2: Runnable = { new Runnable() { override def run(): Unit = () } }
        |  val value3: Runnable = { { new Runnable() { override def run(): Unit = () } } }
        |  val (value4: Runnable) = { new Runnable() { override def run(): Unit = () } }
        |  val (value5, value6) = (
        |    new Runnable() { override def run(): Unit = () },
        |    { new Runnable() { override def run(): Unit = () } },
        |  )
        |
        |  //`var`, local members
        |  def main(args: Array[String]): Unit = {
        |    var value1: Runnable = new Runnable() { override def run(): Unit = () }
        |    var value2: Runnable = { new Runnable() { override def run(): Unit = () } }
        |    var value3: Runnable = { { new Runnable() { override def run(): Unit = () } } }
        |    var (value4: Runnable) = { new Runnable() { override def run(): Unit = () } }
        |    var (value5, value6) = (
        |      new Runnable() { override def run(): Unit = () },
        |      { new Runnable() { override def run(): Unit = () } },
        |    )
        |  }
        |}
        |""".stripMargin

    // different from Scala 2 in `value4`
    val expectedStructureWithAnonymousEnabled =
      s"""-AnonymousClasses_InsideValAndVarBody.scala
         | -MyClass
         |  value1: Runnable
         |  -$$1
         |   run(): Unit
         |  -value2: Runnable
         |   -$$2
         |    run(): Unit
         |  -value3: Runnable
         |   -$EmptyBlockNodeText
         |    -$$3
         |     run(): Unit
         |  -value4
         |   -$$4
         |    run(): Unit
         |  value5
         |  value6
         |  -$$5
         |   run(): Unit
         |  -$$6
         |   run(): Unit
         |  -main(Array[String]): Unit
         |   -$$7
         |    run(): Unit
         |   -$$8
         |    run(): Unit
         |   -$$9
         |    run(): Unit
         |   -$$10
         |    run(): Unit
         |   -$$11
         |    run(): Unit
         |   -$$12
         |    run(): Unit
         |""".stripMargin.trim

    myFixture.configureByText(s"${getTestName(false)}.scala", code)

    //NOTE: our common test code from `ScalaStructureViewTestBase` can't test
    // nodes coming from com.intellij.ide.util.FileStructureNodeProvider
    //In IntelliJ tests they test it using this fixture method
    myFixture.testStructureView { svc =>
      val tree = svc.getTree

      svc.setActionActive(ScalaAnonymousClassesNodeProvider.ID, true)

      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.assertTreeEqual(tree, expectedStructureWithAnonymousEnabled)
    }
  }

  def testEnum_Simple(): Unit = {
    check(
      """enum MyEnum:
        |    case A, B
        |    case C
        |""".stripMargin,
      Node(
        ENUM, "MyEnum",
        Node(EnumCaseIcon, "A"),
        Node(EnumCaseIcon, "B"),
        Node(EnumCaseIcon, "C"),
      )
    )
  }

  def testEnum_WithTypeParameters(): Unit = {
    check(
      """enum ListEnum[+A]:
        |    case Cons(h: A, t: ListEnum[A])
        |    case Empty
        |""".stripMargin,
      Node(
        ENUM, "ListEnum[A]",
        Node(EnumCaseIcon, "Cons(A, ListEnum[A])"),
        Node(EnumCaseIcon, "Empty")
      )
    )
  }

  def testEnum_WithMembersAndParameters(): Unit = {
    check(
      """enum Planet(mass: Double, radius: Double):
        |  private final val G = 6.67300E-11
        |  def surfaceGravity = G * mass / (radius * radius)
        |  def surfaceWeight(otherMass: Double) =  otherMass * surfaceGravity
        |
        |  case Mercury extends Planet(3.303e+23, 2.4397e6)
        |  case Venus   extends Planet(4.869e+24, 6.0518e6)
        |  case Earth   extends Planet(5.976e+24, 6.37814e6)
        |  case Mars    extends Planet(6.421e+23, 3.3972e6)
        |  case Jupiter extends Planet(1.9e+27,   7.1492e7)
        |  case Saturn  extends Planet(5.688e+26, 6.0268e7)
        |  case Uranus  extends Planet(8.686e+25, 2.5559e7)
        |  case Neptune extends Planet(1.024e+26, 2.4746e7)
        |end Planet
        |""".stripMargin,
      Node(
        ENUM, "Planet(Double, Double)",
        Node(layered(FIELD_VAL, FinalMark), PrivateIcon, "G"),
        Node(MethodIcon, "surfaceGravity"),
        Node(MethodIcon, "surfaceWeight(Double)"),
        Node(EnumCaseIcon, "Mercury"),
        Node(EnumCaseIcon, "Venus"),
        Node(EnumCaseIcon, "Earth"),
        Node(EnumCaseIcon, "Mars"),
        Node(EnumCaseIcon, "Jupiter"),
        Node(EnumCaseIcon, "Saturn"),
        Node(EnumCaseIcon, "Uranus"),
        Node(EnumCaseIcon, "Neptune"),
      )
    )
  }

  def testExtensions_SingleMethod(): Unit = {
    check(
      """extension (x: String) def f1: String = ???
        |
        |extension (x: String)
        |  def f1: String = ???
        |""".stripMargin,
      Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String")),
      Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String")),
    )
  }

  def testExtensions_Collective(): Unit = {
    check(
      """extension (x: String)
        |  def f1: String = ???
        |  def f2: String = ???
        |
        |extension (x: String)
        |  def f1: String = ???
        |  def f2: String = ???
        |end extension
        |
        |extension (x: String) {
        |  def f1: String = ???
        |  def f2: String = ???
        |}
        |""".stripMargin,
      Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String"), Node(FUNCTION, "f2: String")),
      Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String"), Node(FUNCTION, "f2: String")),
      Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String"), Node(FUNCTION, "f2: String")),
    )
  }

  def testExtensions_WithParameters_WithContextParameters_WithTypeParameters(): Unit = {
    check(
      """extension (using context1: Int)(x: String)(using context2: Int) {
        |  def f1: String = ???
        |}
        |
        |extension [T1, T2 <: AnyRef](x: String) {
        |  def f2[T3](p: T3) = ???
        |}
        |""".stripMargin,
      Node(EXTENSION, "extension (?=> Int)(String)(?=> Int)", Node(FUNCTION, "f1: String")),
      Node(EXTENSION, "extension [T1, T2 <: AnyRef](String)", Node(FUNCTION, "f2[T3](T3)"))
    )
  }

  def testExtension_InAllScopes(): Unit = {
    check(
      """object Wrapper {
        |  extension (s: String) def f1: String = s + "_1"
        |
        |  def foo1(): Unit = {
        |    extension (s: String) def f2: String = s + "_2"
        |
        |    {
        |      def foo = 1
        |      extension (s: String) def f3: String = s + "_3"
        |      println("test".f3)
        |    }
        |
        |    println("test".f1)
        |    println("test".f2)
        |  }
        |}
        |""".stripMargin,
      Node(OBJECT, "Wrapper",
        Node(EXTENSION, "extension (String)", Node(FUNCTION, "f1: String")),
        Node(MethodIcon, "foo1(): Unit",
          Node(EXTENSION, "extension (String)", Node(FUNCTION, "f2: String")),
          new Node(BlockIcon, "", // using `new` to avoid "public" modifier icon
            Node(FUNCTION, "foo"),
            Node(EXTENSION, "extension (String)", Node(FUNCTION, "f3: String")),
          ),
        ),
      ),
    )
  }

  // FIXME: org.jetbrains.plugins.scala.structureView.ScalaInheritedMembersNodeProvider.nodesOf
  //  currently all inherited extension methods are shown as plain methods
//  def testExtension_Inherited(): Unit = {
//    check(
//      """class Parent {
//        |  extension (s: String)
//        |    def ext1: String = s + "_1"
//        |}
//        |
//        |object Child extends Parent {
//        |
//        |  extension (s: String)
//        |    def ext2: String = s + "_4"
//        |}
//        |""".stripMargin,
//      """[fileScala] foo.scala
//        |[classScala, c_public] Parent
//        |  [function, c_public] extension (String)
//        |    [method, c_public] ext1: String
//        |[objectScala, c_public] Child
//        |  [function, c_public] extension (String)
//        |    [method, c_public] ext2: String""".stripMargin
//    )
//  }

  def testDeprecatedScala3(): Unit = check(
    """
      |@deprecated
      |enum E1:
      |  case A, B
      |  case C
      |
      |enum E2:
      |  @deprecated
      |  case A, B
      |  @deprecated case C
      |  @deprecated
      |  case D(i: Int) extends E2
      |  case E(@deprecated s: String)
      |
      |extension (x: String)
      |  @deprecated def f1: String = ???
      |  @deprecated def f2(i: Int): String = ???
      |
      |trait Foo { val foo: Int }
      |
      |@deprecated
      |given Foo with {
      |  val foo = 1
      |}
      |
      |@deprecated
      |given f: Foo with {
      |  val foo = 2
      |}
      |
      |abstract class C:
      |  @deprecated given f: Foo
      |  @deprecated given String = "foo"
      |
      |""".stripMargin,

    Node(ENUM, "E1", DeprecatedAttributesKey,
      Node(EnumCaseIcon, "A"),
      Node(EnumCaseIcon, "B"),
      Node(EnumCaseIcon, "C")
    ),

    Node(ENUM, "E2",
      Node(EnumCaseIcon, "A", DeprecatedAttributesKey),
      Node(EnumCaseIcon, "B", DeprecatedAttributesKey),
      Node(EnumCaseIcon, "C", DeprecatedAttributesKey),
      Node(EnumCaseIcon, "D(Int)", DeprecatedAttributesKey),
      Node(EnumCaseIcon, "E(String)")
    ),

    Node(EXTENSION, "extension (String)",
      Node(FUNCTION, "f1: String", DeprecatedAttributesKey),
      Node(FUNCTION, "f2(Int): String", DeprecatedAttributesKey)
    ),

    Node(TRAIT, "Foo",
      Node(ABSTRACT_FIELD_VAL, "foo: Int")
    ),

    // TODO: better rendering for givens (SCL-19360)
    Node(CLASS, "Foo with {\n  val foo = 1\n}", DeprecatedAttributesKey,
      Node(FIELD_VAL, "foo")
    ),
    Node(CLASS, "f", DeprecatedAttributesKey,
      Node(FIELD_VAL, "foo")
    ),
    Node(ABSTRACT_CLASS, "C",
      Node(AbstractMethodIcon, "f: Foo", DeprecatedAttributesKey),
      Node(MethodIcon, "given_String: String", DeprecatedAttributesKey)
    )
  )

  def testImplicitAndUsingParams(): Unit = check(
    """
       object Container {
         class C1(i: Int)(using s: String)
         class C2(i: Int) {
           def this()(using s: String) = this(s.length)
         }
         class C3(using val i: Int)(s: String)(using d: Double)(implicit val b: Boolean)
         def m1(using i: Int, s: String): Unit = {}
         def m2[A, B, C, D](using a: A)(b: B)(using c: C, d: D): Unit = {}
         extension [T](x: T)(using n: Numeric[T])
           def + (y: T): T = n.plus(x, y)
           def foo(i: Int)(using n2: Numeric[Int]): Unit =
             println(n2.plus(i, 2))
             println(n.plus(x, x))
       }
    """,
    Node(OBJECT, "Container",
      Node(CLASS, "C1(Int)(?=> String)"),
      Node(CLASS, "C2(Int)",
        Node(MethodIcon, "this()(?=> String)")
      ),
      Node(CLASS, "C3(?=> Int)(String)(?=> Double)(?=> Boolean)",
        Node(FIELD_VAL, "i: Int"),
        Node(FIELD_VAL, "b: Boolean"),
      ),
      Node(MethodIcon, "m1(?=> Int, String): Unit"),
      Node(MethodIcon, "m2[A, B, C, D](?=> A)(B)(?=> C, D): Unit"),
      Node(EXTENSION, "extension [T](T)(?=> Numeric[T])",
        Node(FUNCTION, "+(T): T"),
        Node(FUNCTION, "foo(Int)(?=> Numeric[Int]): Unit"),
      ),
    )
  )

  def testInheritedMembersScala3(): Unit = {
    @Language("Scala 3")
    val baseClass =
      """package tests
        |
        |class Base:
        |  enum InnerEnum:
        |    case A
        |    case B, C
        |
        |  given g1: Int = 1
        |  given g2: AutoCloseable with
        |    override def close(): Unit = {}
        |
        |  extension (s: String)
        |    def scream: String = s.toUpperCase
        |end Base
        |""".stripMargin
    myFixture.addFileToProject("tests/Base.scala", baseClass)

    @Language("Scala 3")
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
           |  g1: Int
           |  -g2
           |   clone(): Object
           |   close(): Unit
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
           |  getClass(): Class[_]
           |  hashCode(): Int
           |  -InnerEnum
           |   A
           |   B
           |   C
           |   canEqual(Any): Boolean
           |   clone(): Object
           |   equals(Object): Boolean
           |   finalize(): Unit
           |   getClass(): Class[_]
           |   hashCode(): Int
           |   notify(): Unit
           |   notifyAll(): Unit
           |   ordinal: Int
           |   productArity: Int
           |   productElement(Int): Any
           |   productElementName(Int): String
           |   productElementNames: Iterator[String]
           |   productIterator: Iterator[Any]
           |   productPrefix: String
           |   toString(): String
           |   wait(): Unit
           |   wait(Long): Unit
           |   wait(Long, Int): Unit
           |  notify(): Unit
           |  notifyAll(): Unit
           |  scream: String
           |  toString(): String
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
           |   g1: Int
           |   scream: String
           |  -g2
           |   close(): Unit
           |   -Object
           |    clone(): Object
           |    equals(Object): Boolean
           |    finalize(): Unit
           |    getClass(): Class[_]
           |    hashCode(): Int
           |    notify(): Unit
           |    notifyAll(): Unit
           |    toString(): String
           |    wait(): Unit
           |    wait(Long): Unit
           |    wait(Long, Int): Unit
           |  -InnerEnum
           |   A
           |   B
           |   C
           |   -Enum
           |    ordinal: Int
           |   -Equals
           |    canEqual(Any): Boolean
           |   -Object
           |    clone(): Object
           |    equals(Object): Boolean
           |    finalize(): Unit
           |    getClass(): Class[_]
           |    hashCode(): Int
           |    notify(): Unit
           |    notifyAll(): Unit
           |    toString(): String
           |    wait(): Unit
           |    wait(Long): Unit
           |    wait(Long, Int): Unit
           |   -Product
           |    productArity: Int
           |    productElement(Int): Any
           |    productElementName(Int): String
           |    productElementNames: Iterator[String]
           |    productIterator: Iterator[Any]
           |    productPrefix: String
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

  def testNavigationFromSourceScala3(): Unit = checkNavigationFromSource(
    s"""
       |val to${CARET}pX = 1
       |private var (_, to${CARET}pY) = (true, 2)
       |
       |def to${CARET}pM(using I${CARET}nt) = {${CARET}}
       |
       |abstract class Exa${CARET}mple(
       |  classParam1${CARET}UnusedInBody: String,
       |  classParam2UsedI${CARET}nBody: String,
       |  val clas${CARET}sParam3: String
       |):
       |  def th${CARET}is() = th${CARET}is(???, ???, ???)
       |
       |  val myV${CARET}al1 = ???
       |  val (myVal2, myV${CARET}al3) = ???
       |  protected lazy val myV${CARET}al4: Int
       |
       |  private var my${CARET}Var: Boolean = true
       |
       |  def myD${CARET}ef(par${CARET}am: String): String = classParam${CARET}2UsedInBody
       |  def myAbstra${CARET}ctDef(using Int)(s: String)(using Boolean): Unit
       |
       |  extens${CARET}ion (s${CARET}: String)
       |    def myExtens${CARET}ionMethod: String = ???
       |
       |  given myG${CARET}iven: String = ???
       |  protected gi${CARET}ven myAbstractGiven: Int
       |
       |  given Lo${CARET}ng = ???
       |
       |  given c${CARET}s: CharSequence with:
       |    override def length(): Int = ???
       |    override def cha${CARET}rAt(index: Int): Char = ???
       |    override def subSequence(start: Int, end: Int): CharSequence = ???
       |
       |  given AutoC${CARET}loseable with:
       |    override de${CARET}f close(): Unit = {}
       |
       |  type MyTy${CARET}peAlias = String
       |  type MyAbst${CARET}ractTypeAlias[T]
       |
       |  class My${CARET}Class
       |  trait My${CARET}Trait
       |  object MyO${CARET}bject
       |
       |  enum My${CARET}Enum:
       |    case MyCase1
       |    case MyCase2, MyC${CARET}ase3
       |    case MyCa${CARET}se4(x: Int) extends MyEnum
       |
       |    private def myEnu${CARET}mFun() = this.toString
       |    val myEnu${CARET}mVal: Boolean = false
       |  end MyEnum
       |e${CARET}nd Exam${CARET}ple
       |""".stripMargin,
    Node(VAL, "topX"), // toplevel val topX
    Node(VAR, PrivateIcon, "topY"), // toplevel var topY
    Node(FUNCTION, "topM(?=> Int)"), // toplevel def topM
    Node(FUNCTION, "topM(?=> Int)"), // topM function param `using Int`
    Node(FUNCTION, "topM(?=> Int)"), // topM function body
    Node(ABSTRACT_CLASS, "Example(String, String, String)"), // class Example
    Node(ABSTRACT_CLASS, "Example(String, String, String)"), // class param classParam1UnusedInBody
    Node(ABSTRACT_CLASS, "Example(String, String, String)"), // class param classParam2UsedInBody
    Node(FIELD_VAL, "classParam3: String"), // class val param classParam3
    Node(MethodIcon, "this()"), // def this()
    Node(MethodIcon, "this()"), // def this() body
    Node(FIELD_VAL, "myVal1"), // val myVal1
    Node(FIELD_VAL, "myVal3"), // val myVal3
    Node(ABSTRACT_FIELD_VAL, ProtectedIcon, "myVal4: Int"), // lazy val myVal4
    Node(FIELD_VAR, PrivateIcon, "myVar: Boolean"), // var myVar
    Node(MethodIcon, "myDef(String): String"), // def myDef
    Node(MethodIcon, "myDef(String): String"), // myDef method param `param`
    Node(MethodIcon, "myDef(String): String"), // myDef method body
    Node(AbstractMethodIcon, "myAbstractDef(?=> Int)(String)(?=> Boolean): Unit"), // def myAbstractDef
    Node(EXTENSION, "extension (String)"), // extension (s: String)
    Node(EXTENSION, "extension (String)"), // extension param s
    Node(FUNCTION, "myExtensionMethod: String"), // extension method def myExtensionMethod
    Node(MethodIcon, "myGiven: String"), // given myGiven
    Node(AbstractMethodIcon, ProtectedIcon, "myAbstractGiven: Int"), // protected given myAbstractImplicitDef
    Node(MethodIcon, "given_Long: Long"), // given Long
    Node(CLASS, "cs"), // given cs: CharSequence...
    Node(MethodIcon, "charAt(Int): Char"), // method charAt inside given cs
    Node(CLASS,
      """AutoCloseable with:
        |    override def close(): Unit = {}""".stripMargin), // given AutoCloseable...
    Node(MethodIcon, "close(): Unit"), // method close inside given AutoCloseable
    Node(TYPE_ALIAS, "MyTypeAlias"), // type MyTypeAlias
    Node(ABSTRACT_TYPE_ALIAS, "MyAbstractTypeAlias"), // type MyAbstractTypeAlias
    Node(CLASS, "MyClass"), // class MyClass
    Node(TRAIT, "MyTrait"), // class MyTrait
    Node(OBJECT, "MyObject"), // class MyObject
    Node(ENUM, "MyEnum"), // class MyObject
    Node(EnumCaseIcon, "MyCase3"), // enum case MyCase3
    Node(EnumCaseIcon, "MyCase4(Int)"), // enum case MyCase4
    Node(MethodIcon, PrivateIcon, "myEnumFun()"), // enum method myEnumFun
    Node(FIELD_VAL, "myEnumVal: Boolean"), // enum variable val myEnumVal
    Node(ABSTRACT_CLASS, "Example(String, String, String)"), // end keyword of Example class end marker
    Node(ABSTRACT_CLASS, "Example(String, String, String)"), // identifier of Example class end marker
  )
}
