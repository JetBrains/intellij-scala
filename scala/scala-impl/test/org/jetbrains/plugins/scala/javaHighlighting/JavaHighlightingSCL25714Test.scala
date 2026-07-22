package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class JavaHighlightingSCL25714Test extends JavaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13 || version == LatestScalaVersions.Scala_3

  def testJavaTraitImplementationDoesNotInheritScalaTraitSuperclass(): Unit = {
    assertErrorsTextInJava(
      """abstract class ScalaBase {
        |  def fooScalaBaseConcrete(): Unit = {}
        |  def fooScalaBaseImplementedByScalaChild(): Unit
        |  def fooScalaBaseImplementedByScalaChildOrScalaTrait(): Unit
        |}
        |
        |trait ScalaMixed extends ScalaBase {
        |  override def fooScalaBaseImplementedByScalaChildOrScalaTrait(): Unit = {}
        |  def fooScalaMixedConcrete(): Unit = {}
        |}
        |
        |trait ScalaIndirectMixed extends ScalaMixed
        |
        |trait ScalaOrdinaryTrait {
        |  def fooScalaOrdinaryTraitConcrete(): Unit = {}
        |}
        |
        |abstract class ScalaDirectChild extends ScalaMixed {
        |  override def fooScalaBaseImplementedByScalaChild(): Unit = {}
        |  def fooScalaDirectChildConcrete(): Unit = {}
        |}
        |
        |abstract class ScalaIndirectChild extends ScalaIndirectMixed {
        |  override def fooScalaBaseImplementedByScalaChild(): Unit = {}
        |  def fooScalaIndirectChildConcrete(): Unit = {}
        |}
        |
        |class ScalaOrdinaryTraitChild extends ScalaOrdinaryTrait
        |""".stripMargin,
      """abstract class JavaViaScalaMixed implements ScalaMixed {
        |    void checkVisibleMethods() {
        |        fooScalaMixedConcrete();
        |        fooScalaBaseImplementedByScalaChildOrScalaTrait();
        |
        |        fooScalaBaseConcrete();
        |        fooScalaBaseImplementedByScalaChild();
        |    }
        |}
        |
        |class JavaChildOfScalaDirectChild extends ScalaDirectChild {
        |    void checkInheritedMethods() {
        |        fooScalaDirectChildConcrete();
        |        fooScalaMixedConcrete();
        |        fooScalaBaseConcrete();
        |        fooScalaBaseImplementedByScalaChildOrScalaTrait();
        |        fooScalaBaseImplementedByScalaChild();
        |    }
        |}
        |
        |class JavaUsageOfScalaClasses {
        |    void checkScalaChildren() {
        |        ScalaDirectChild scalaDirectChild = null;
        |        scalaDirectChild.fooScalaDirectChildConcrete();
        |        scalaDirectChild.fooScalaMixedConcrete();
        |        scalaDirectChild.fooScalaBaseConcrete();
        |        scalaDirectChild.fooScalaBaseImplementedByScalaChildOrScalaTrait();
        |        scalaDirectChild.fooScalaBaseImplementedByScalaChild();
        |
        |        ScalaIndirectChild scalaIndirectChild = null;
        |        scalaIndirectChild.fooScalaIndirectChildConcrete();
        |        scalaIndirectChild.fooScalaMixedConcrete();
        |        scalaIndirectChild.fooScalaBaseConcrete();
        |        scalaIndirectChild.fooScalaBaseImplementedByScalaChildOrScalaTrait();
        |        scalaIndirectChild.fooScalaBaseImplementedByScalaChild();
        |
        |        ScalaOrdinaryTraitChild scalaOrdinaryTraitChild = null;
        |        scalaOrdinaryTraitChild.fooScalaOrdinaryTraitConcrete();
        |    }
        |}
        |""".stripMargin,
      javaClassName = "JavaUsageOfScalaClasses",
      ""
      //TODO: uncomment when (IF) SCL-25714 is fixed.
      // Note, there is a chance there is no clean way to fix it without making the code overcomplicated and we will decide to leave with it.
      // This issue is not that critical anyway. It's about java-scala interop and about "Bad code is green", which is not that bad.
//      """Error(fooScalaBaseConcrete,Cannot resolve method 'fooScalaBaseConcrete' in 'JavaViaScalaMixed')
//        |Error(fooScalaBaseImplementedByScalaChild,Cannot resolve method 'fooScalaBaseImplementedByScalaChild' in 'JavaViaScalaMixed')""".stripMargin
    )
  }

  def testGenericMembersInheritedThroughDirectAndIndirectScalaTraitParents(): Unit = {
    assertNoErrorsInJava(
      """abstract class ScalaBase[A] {
        |  def accept(value: A): Unit
        |  def result(): A
        |}
        |
        |trait ScalaMixed[A] extends ScalaBase[A]
        |trait ScalaIndirectMixed[A] extends ScalaMixed[A]
        |
        |abstract class ScalaDirectChild extends ScalaMixed[String]
        |abstract class ScalaIndirectChild extends ScalaIndirectMixed[String]
        |""".stripMargin,
      """class JavaUsageOfScalaChildren {
        |    void check(ScalaDirectChild direct, ScalaIndirectChild indirect) {
        |        String directResult = direct.result();
        |        direct.accept("direct");
        |
        |        String indirectResult = indirect.result();
        |        indirect.accept("indirect");
        |    }
        |}
        |""".stripMargin,
      javaClassName = "JavaUsageOfScalaChildren"
    )
  }

  def testGenericMembersInheritedThroughDirectAndIndirectScalaTraitParentsRejectWrongArgumentTypes(): Unit = {
    assertErrorsTextInJava(
      """abstract class ScalaBase[A] {
        |  def accept(value: A): Unit
        |}
        |
        |trait ScalaMixed[A] extends ScalaBase[A]
        |trait ScalaIndirectMixed[A] extends ScalaMixed[A]
        |
        |abstract class ScalaDirectChild extends ScalaMixed[String]
        |abstract class ScalaIndirectChild extends ScalaIndirectMixed[String]
        |""".stripMargin,
      """class JavaUsageOfScalaChildren {
        |    void check(ScalaDirectChild direct, ScalaIndirectChild indirect) {
        |        direct.accept(42);
        |        indirect.accept(42);
        |    }
        |}
      |""".stripMargin,
      javaClassName = "JavaUsageOfScalaChildren",
      """Error((42),'accept(java.lang.String)' in 'ScalaBase' cannot be applied to '(int)')
        |Error((42),'accept(java.lang.String)' in 'ScalaBase' cannot be applied to '(int)')""".stripMargin
    )
  }
}
