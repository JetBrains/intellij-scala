package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.base.CaptureCheckingTestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3CaptureCheckingFormatterTest extends Scala3FormatterBaseTest {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_8

  override protected def setUp(): Unit = {
    super.setUp()
    CaptureCheckingTestUtils.enableCaptureCheckingInLightModule(getModule, version, getTestRootDisposable)
  }

  /**
   * Creates a physical file in the configured module so committed formatting edits
   * use its Scala version and capture-checking option. Pushing features onto a light
   * virtual file alone does not configure the module features used during reparsing.
   */
  override protected def initFile(fileName: String, text: String): PsiFile = inWriteAction {
    val root = ModuleRootManager.getInstance(getModule).getSourceRoots.head
    val virtualFile = root.createChildData(this, fileName)
    VfsUtil.saveText(virtualFile, text)
    PsiManager.getInstance(getProject).findFile(virtualFile)
  }

  def test_capture_type(): Unit =
    doTextTest(
      """
        |x: A  ^
        |x: A  ^  { }
        |x: left  ^  right
        |x: left  ^  (right)
        |x: left  ^  1
        |x: left  ^  "literal"
        |x: (left  ^  )  ^  right
        |x: arg  ^  ->  ret
        |x: arg  ^  ?->  ret
        |""".stripMargin,
      """
        |x: A^
        |x: A^{}
        |x: left ^ right
        |x: left ^ (right)
        |x: left ^ 1
        |x: left ^ "literal"
        |x: (left^) ^ right
        |x: arg^ -> ret
        |x: arg^ ?-> ret
        |""".stripMargin
    )

  def test_pure_function(): Unit =
    doTextTest(
      """
        |x: A->B  ->  C
        |x: A?->B  ?-> C
        |x: A   ->  { a }  B
        |x: A  ?->  { a }  B
        |""".stripMargin,
      """
        |x: A -> B -> C
        |x: A ?-> B ?-> C
        |x: A ->{a} B
        |x: A ?->{a} B
        |""".stripMargin
    )

  def test_capture_set(): Unit =
    checkStableFormatting(
      """
        |x: A^{  id  .  id  .  rd  }
        |x: A^{  id  .  this  .  this  .  super  .  id  }
        |x: A^{  super  [  x  .  y  ]  .  id  }
        |x: A^{  this  .  id  .  id  .  as  [  x  ]  .  rd  }
        |x: A^{  this  *  }
        |x: A^{  this  *  .  as  [  x  ]  }
        |x: A^{  this  *  .  rd  }
        |""".stripMargin,
      """
        |x: A^{id.id.rd}
        |x: A^{id.this.this.super.id}
        |x: A^{super[x.y].id}
        |x: A^{this.id.id.as[x].rd}
        |x: A^{this*}
        |x: A^{this*.as[x]}
        |x: A^{this*.rd}
        |""".stripMargin
    )

  def test_capture_filters(): Unit =
    doTextTest(
      """
        |x: Object^{ any . only [ SharedCapability ] }
        |x: Object^{ c . except [ C ] . except [ D ] }
        |x: Object^{ this . c . only [ p . C ] . except [ p . D ] . rd }
        |x: () -> { any . except [ Control ] } Unit
        |x: () ?-> { c * . only [ C ] } Unit
        |x: () -> { this . only [ UserTable ] } List [ User ]
        |x: () -> { this . except [ TransactionTable ] } List [ User ]
        |x: Object^{ c . except [ Any ] , d }
        |x: Option [ Failure ^ { f . only [ Control ] } ]
        |x: Object^{ c . only [ SharedCapability ] . except [ Control ] . except [ IOCap ] }
        |x: Object^{ c . except [ Control ] . rd }
        |x: Object^{ any . only [ Unscoped ] . rd }
        |x: this . Raise ?-> { any . except [ ErrHandler ] } A
        |x: Scope ?-> { any . only [ Shared ] } A
        |""".stripMargin,
      """
        |x: Object^{any.only[SharedCapability]}
        |x: Object^{c.except[C].except[D]}
        |x: Object^{this.c.only[p.C].except[p.D].rd}
        |x: () ->{any.except[Control]} Unit
        |x: () ?->{c*.only[C]} Unit
        |x: () ->{this.only[UserTable]} List[User]
        |x: () ->{this.except[TransactionTable]} List[User]
        |x: Object^{c.except[Any], d}
        |x: Option[Failure^{f.only[Control]}]
        |x: Object^{c.only[SharedCapability].except[Control].except[IOCap]}
        |x: Object^{c.except[Control].rd}
        |x: Object^{any.only[Unscoped].rd}
        |x: this.Raise ?->{any.except[ErrHandler]} A
        |x: Scope ?->{any.only[Shared]} A
        |""".stripMargin
    )

  def test_capture_set_parameters_and_bounds(): Unit =
    doTextTest(
      """def f[ T , C ^ , U ](x: T): U = ???
        |def g[ C ^ >: { } <: { any } , T ](x: Object ^ { C }): T = ???
        |def h[ c ^ <: { any . except [ C1 ] } ](x: A ^ { c }): A ^ { c . except [ C2 ] } = x
        |def run[ T , C ^ ](body: () -> { C } T): Box [ T ] ^ { C . except [ Control ] } = ???
        |def apply[ T , C ^ ](body: () -> { C } T): Try [ T ] ^ { C . only [ Control ] } = ???
        |def withTransaction[ T , E ^ ](body: (tx: Transaction ^) -> (() -> { E , tx . except [ Commit ] . except [ Rollback ] } T)): T = ???
        |""".stripMargin,
      """def f[T, C^, U](x: T): U = ???
        |def g[C^ >: {} <: {any}, T](x: Object^{C}): T = ???
        |def h[c^ <: {any.except[C1]}](x: A^{c}): A^{c.except[C2]} = x
        |def run[T, C^](body: () ->{C} T): Box[T]^{C.except[Control]} = ???
        |def apply[T, C^](body: () ->{C} T): Try[T]^{C.only[Control]} = ???
        |def withTransaction[T, E^](body: (tx: Transaction^) -> (() ->{E, tx.except[Commit].except[Rollback]} T)): T = ???
        |""".stripMargin
    )

  def test_same_line_trailing_comma_in_capture_bound(): Unit =
    checkStableFormatting(
      """class Box[C ^ <: { any , }, T]
        |class After""".stripMargin,
      """class Box[C^ <: {any,}, T]
        |
        |class After""".stripMargin
    )

  def test_multiline_trailing_comma_in_capture_bound(): Unit =
    checkStableFormatting(
      """class Box[C ^ <: { any ,
        |}, T]
        |class After""".stripMargin,
      """class Box[C^ <: {any,
        |}, T]
        |
        |class After""".stripMargin
    )

  def test_capture_bounds_with_comments_and_line_breaks(): Unit =
    checkStableFormatting(
      """def f[
        |  C ^ >: /* lower */ { } <: /* upper */ { any . only [ Shared ] . except [ Control ] . rd },
        |  D ^ <: // capture upper
        |    { any },
        |  T >: /* type lower */ Nothing <: // type upper
        |    AnyRef
        |](x: T): T = x
        |""".stripMargin,
      """def f[
        |  C^ >: /* lower */ {} <: /* upper */ {any.only[Shared].except[Control].rd},
        |  D^ <: // capture upper
        |  {any},
        |  T >: /* type lower */ Nothing <: // type upper
        |  AnyRef
        |](x: T): T = x
        |""".stripMargin
    )

  /**
   * Checks the expected document and PSI text after each of two passes on the same file.
   *
   * @param before source used to create the file before the first formatting pass
   * @param expected text expected after each pass and during the final verification
   */
  private def checkStableFormatting(
    before: String,
    expected: String
  ): Unit =
    doTextTest(
      before,
      expected,
      repeats = 2,
      checkAfterEachIteration = true
    )
}
