package org.jetbrains.plugins.scala.lang.stubIndex

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMember}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitInstanceIndex}
import org.junit.Assert

import scala.language.implicitConversions
import scala.reflect.ClassTag

class StubIndexTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override implicit val version: ScalaVersion = Scala_2_12

  private def intKey(s: String): Integer = Integer.valueOf(s.hashCode)

  private def moduleWithLibraries: GlobalSearchScope = GlobalSearchScope.moduleWithLibrariesScope(module)

  private def elementsInScalaLibrary[Key, Psi <: PsiElement : ClassTag](key: Key, indexKey: StubIndexKey[Key, Psi]): Seq[Psi] = {
    val classOfPsi = implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
    indexKey.elements(key, moduleWithLibraries, classOfPsi)(getProject).toList
  }

  private def fqnsInScalaLibrary[Key, Psi <: PsiMember : ClassTag](key: Key, indexKey: StubIndexKey[Key, Psi]): Seq[String] = {
    elementsInScalaLibrary(key, indexKey).flatMap(_.qualifiedNameOpt).sorted
  }

  private def checkFQNamesFromIndex[Key, Psi <: PsiMember : ClassTag](indexKey: StubIndexKey[Key, Psi], key: Key)
                                                                     (expected: String*): Unit = {
    Assert.assertEquals(expected.mkString("\n"), fqnsInScalaLibrary(key, indexKey).mkString("\n"))
  }

  private def assertContains[T](set: Set[T], element: T): Unit = {
    Assert.assertTrue(s"$element not found", set.contains(element))
  }

  def testAllClassNames(): Unit = {
    checkFQNamesFromIndex(ALL_CLASS_NAMES, "HashSet")( //classes
      "scala.collection.immutable.HashSet",
      "scala.collection.mutable.HashSet"
    )
    checkFQNamesFromIndex(ALL_CLASS_NAMES, "HashSet$")( //companion objects
      "scala.collection.immutable.HashSet",
      "scala.collection.mutable.HashSet"
    )
  }

  def testShortName(): Unit = {
    checkFQNamesFromIndex(SHORT_NAME_KEY, "HashSet")(
      "scala.collection.immutable.HashSet",
      "scala.collection.immutable.HashSet",
      "scala.collection.mutable.HashSet",
      "scala.collection.mutable.HashSet"
    )
  }

  //inside package objects
  def testNotVisibleInJava(): Unit = {
    checkFQNamesFromIndex(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, "DurationLong")("scala.concurrent.duration.DurationLong")
  }

  def testFQN(): Unit = {
    checkFQNamesFromIndex(FQN_KEY, intKey("scala.collection.immutable.HashSet"))("scala.collection.immutable.HashSet", "scala.collection.immutable.HashSet")
  }

  def testPackageObject(): Unit = {
    checkFQNamesFromIndex(PACKAGE_OBJECT_KEY, intKey("scala.collection"))("scala.collection")
  }

  def testPackageObjectShort(): Unit = {
    checkFQNamesFromIndex(PACKAGE_OBJECT_SHORT_NAME_KEY, "collection")("scala.collection")
  }

  def testPackageFQN(): Unit = {
    val packagings = elementsInScalaLibrary(intKey("scala.math"), PACKAGE_FQN_KEY)
    Assert.assertEquals("Wrong number of packagings found: ", 14, packagings.size)
  }

  def testMethodName(): Unit = {
    checkFQNamesFromIndex(METHOD_NAME_KEY, "implicitly")("scala.Predef.implicitly")

    checkFQNamesFromIndex(METHOD_NAME_KEY, "toOption")(
      "scala.util.Either.LeftProjection.toOption",
      "scala.util.Either.RightProjection.toOption",
      "scala.util.Either.toOption",
      "scala.util.Failure.toOption",
      "scala.util.Success.toOption",
      "scala.util.Try.toOption",
      "scala.util.control.Exception.Catch.toOption"
    )
  }

  def testClassNameInPackage(): Unit = {
    checkFQNamesFromIndex(CLASS_NAME_IN_PACKAGE_KEY, "scala.concurrent.duration")(
      "scala.concurrent.duration.Deadline",
      "scala.concurrent.duration.Deadline",
      "scala.concurrent.duration.DoubleMult",
      "scala.concurrent.duration.Duration",
      "scala.concurrent.duration.Duration",
      "scala.concurrent.duration.DurationConversions",
      "scala.concurrent.duration.DurationConversions",
      "scala.concurrent.duration.DurationDouble",
      "scala.concurrent.duration.DurationInt",
      "scala.concurrent.duration.DurationLong",
      "scala.concurrent.duration.FiniteDuration",
      "scala.concurrent.duration.FiniteDuration",
      "scala.concurrent.duration.IntMult",
      "scala.concurrent.duration.LongMult",
      "scala.concurrent.duration.fromNow",
      "scala.concurrent.duration.span"
    )
  }

  def testJavaClassNameInPackage(): Unit = {
    checkFQNamesFromIndex(JAVA_CLASS_NAME_IN_PACKAGE_KEY, "scala.concurrent.duration")(
      "scala.concurrent.duration",
      "scala.concurrent.duration.Deadline",
      "scala.concurrent.duration.Deadline",
      "scala.concurrent.duration.Duration",
      "scala.concurrent.duration.Duration",
      "scala.concurrent.duration.DurationConversions",
      "scala.concurrent.duration.DurationConversions",
      "scala.concurrent.duration.FiniteDuration",
      "scala.concurrent.duration.FiniteDuration"
    )
  }

  def testImplicitObjects(): Unit = {
    checkFQNamesFromIndex(IMPLICIT_OBJECT_KEY, "scala.math.Ordering")(
      "scala.math.Ordering.BigDecimal",
      "scala.math.Ordering.BigInt",
      "scala.math.Ordering.Boolean",
      "scala.math.Ordering.Byte",
      "scala.math.Ordering.Char",
      "scala.math.Ordering.Double",
      "scala.math.Ordering.Float",
      "scala.math.Ordering.Int",
      "scala.math.Ordering.Long",
      "scala.math.Ordering.Short",
      "scala.math.Ordering.String",
      "scala.math.Ordering.Unit"
    )
  }

  def testAnnotatedMembers(): Unit = {
    val annotations = elementsInScalaLibrary("implicitNotFound", ANNOTATED_MEMBER_KEY)
    val annotated = annotations.map(PsiTreeUtil.getParentOfType(_, classOf[ScMember])).flatMap(_.qualifiedNameOpt).toSet
    Assert.assertEquals(14, annotated.size)
    assertContains(annotated, "scala.reflect.ClassTag")
    assertContains(annotated, "scala.Predef.=:=")
    assertContains(annotated, "scala.Function1")
  }

  def testPropertyName(): Unit = {
    checkFQNamesFromIndex(PROPERTY_NAME_KEY, "array")(
      "scala.collection.mutable.ArraySeq.array",
      "scala.collection.mutable.ResizableArray.array",
      "scala.collection.parallel.mutable.ExposedArraySeq.array"
    )
  }

  def testClassParameterName(): Unit = {
    checkFQNamesFromIndex(CLASS_PARAMETER_NAME_KEY, "queue")(
      "scala.ref.PhantomReference.queue",
      "scala.ref.PhantomReferenceWithWrapper.queue",
      "scala.ref.SoftReference.queue",
      "scala.ref.SoftReferenceWithWrapper.queue",
      "scala.ref.WeakReference.queue",
      "scala.ref.WeakReferenceWithWrapper.queue"
    )
  }

  def testTypeAlias(): Unit = {
    checkFQNamesFromIndex(TYPE_ALIAS_NAME_KEY, "String")("scala.Predef.String")
    checkFQNamesFromIndex(TYPE_ALIAS_NAME_KEY, "Throwable")("scala.Throwable")
    checkFQNamesFromIndex(TYPE_ALIAS_NAME_KEY, "Catcher")("scala.util.control.Exception.Catcher")
    checkFQNamesFromIndex(TYPE_ALIAS_NAME_KEY, "Configure")("scala.io.Codec.Configure")
  }

  def testStableAlias(): Unit = {
    checkFQNamesFromIndex(STABLE_ALIAS_NAME_KEY, "String")("scala.Predef.String")
    checkFQNamesFromIndex(STABLE_ALIAS_NAME_KEY, "Throwable")("scala.Throwable")
    checkFQNamesFromIndex(STABLE_ALIAS_NAME_KEY, "Catcher")("scala.util.control.Exception.Catcher")
    checkFQNamesFromIndex(STABLE_ALIAS_NAME_KEY, "Configure")()
  }

  def testSuperClassName(): Unit = {
    def classFQN(extendsBlock: ScExtendsBlock) = extendsBlock.getParent.asInstanceOf[ScTypeDefinition].qualifiedName

    val optionInheritors = elementsInScalaLibrary("Option", SUPER_CLASS_NAME_KEY).map(classFQN).sorted
    Assert.assertEquals(optionInheritors, Seq("scala.None", "scala.Some"))

    val function2Inheritors = elementsInScalaLibrary("Function2", SUPER_CLASS_NAME_KEY).map(classFQN).sorted
    Assert.assertEquals(function2Inheritors, Seq("scala.runtime.AbstractFunction2"))

    Assert.assertTrue(elementsInScalaLibrary("AnyRef", SUPER_CLASS_NAME_KEY).size > 1000)
    Assert.assertTrue(elementsInScalaLibrary("Object", SUPER_CLASS_NAME_KEY).size > 1000)
  }

  def testSelfType(): Unit = {
    val elements = elementsInScalaLibrary("Runnable", SELF_TYPE_CLASS_NAME_KEY)
    val containingClass = elements.map(PsiTreeUtil.getParentOfType(_, classOf[ScTypeDefinition])).map(_.qualifiedName)
    Assert.assertEquals(Seq("scala.concurrent.OnCompleteRunnable"), containingClass)
  }

  def testImplicitConversion(): Unit = {
    val all = ImplicitConversionIndex.allElements(moduleWithLibraries).flatMap(_.qualifiedNameOpt).toSet
    Assert.assertEquals(286, all.size)
    assertContains(all, "scala.math.Ordering.mkOrderingOps")
    assertContains(all, "scala.math.Ordering.ExtraImplicits.infixOrderingOps")
    assertContains(all, "scala.Predef.augmentString")
    assertContains(all, "scala.Predef.ArrowAssoc")
  }

  def testImplicitInstance(): Unit = {
    def forClassFqn(fqn: String): Set[String] =
      ImplicitInstanceIndex.forClassFqn(fqn, moduleWithLibraries, project).flatMap(_.qualifiedNameOpt).toSet

    val orderings = forClassFqn("scala.math.Ordering")
    Assert.assertEquals(13, orderings.size)
    assertContains(orderings, "scala.math.LowPriorityOrderingImplicits.ordered")
    assertContains(orderings, "scala.math.Ordering.ExtraImplicits.seqDerivedOrdering")

    val booleanOrdering = forClassFqn("scala.math.Ordering.BooleanOrdering")
    Assert.assertEquals(Set("scala.math.Ordering.Boolean"), booleanOrdering)

    val executionContext = forClassFqn("scala.concurrent.ExecutionContext")
    Assert.assertEquals(Set("scala.concurrent.ExecutionContext.Implicits.global"), executionContext)
  }
}
