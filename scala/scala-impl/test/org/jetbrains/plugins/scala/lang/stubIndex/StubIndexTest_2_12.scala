package org.jetbrains.plugins.scala
package lang
package stubIndex

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMember}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitInstanceIndex}
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.AnyFqn
import org.junit.Assert._

import scala.language.implicitConversions
import scala.reflect.ClassTag

class StubIndexTest_2_12 extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_12

  private def intKey(s: String): Integer = Integer.valueOf(s.hashCode)

  private def moduleWithLibraries: GlobalSearchScope = GlobalSearchScope.moduleWithLibrariesScope(getModule)

  private def elementsInScalaLibrary[Key, Psi <: PsiElement : ClassTag](key: Key, indexKey: StubIndexKey[Key, Psi]): Seq[Psi] = {
    indexKey.elements(key, moduleWithLibraries)(getProject).toList
  }

  private def fqnsInScalaLibrary[Key, Psi <: PsiMember : ClassTag](key: Key, indexKey: StubIndexKey[Key, Psi]): Seq[String] = {
    elementsInScalaLibrary(key, indexKey).flatMap(_.qualifiedNameOpt).sorted
  }

  private def checkFQNamesFromIndex[Key, Psi <: PsiMember : ClassTag](indexKey: StubIndexKey[Key, Psi], key: Key)
                                                                     (expected: String*): Unit = {
    assertEquals(expected.mkString("\n"), fqnsInScalaLibrary(key, indexKey).mkString("\n"))
  }

  private def assertContains[T](set: Set[T], element: T): Unit = {
    assertTrue(s"$element not found", set.contains(element))
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
    assertEquals("Wrong number of packagings found: ", 14, packagings.size)
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
    assertEquals(14, annotated.size)
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
    assertEquals(optionInheritors, Seq("scala.None", "scala.Some"))

    val function2Inheritors = elementsInScalaLibrary("Function2", SUPER_CLASS_NAME_KEY).map(classFQN).sorted
    assertEquals(function2Inheritors, Seq("scala.runtime.AbstractFunction2"))

    assertTrue(elementsInScalaLibrary("AnyRef", SUPER_CLASS_NAME_KEY).size > 1000)
    assertTrue(elementsInScalaLibrary("Object", SUPER_CLASS_NAME_KEY).size > 1000)
  }

  def testSelfType(): Unit = {
    val elements = elementsInScalaLibrary("Runnable", SELF_TYPE_CLASS_NAME_KEY)
    val containingClass = elements.map(PsiTreeUtil.getParentOfType(_, classOf[ScTypeDefinition])).map(_.qualifiedName)
    assertEquals(Seq("scala.concurrent.OnCompleteRunnable"), containingClass)
  }

  def testImplicitInstance(): Unit = {
    def forClassFqn(fqn: String) =
      ImplicitInstanceIndex.forClassFqn(fqn, moduleWithLibraries)(getProject).flatMap(_.qualifiedNameOpt)

    val orderings = forClassFqn("scala.math.Ordering")
    assertEquals(13, orderings.size)
    assertContains(orderings, "scala.math.LowPriorityOrderingImplicits.ordered")
    assertContains(orderings, "scala.math.Ordering.ExtraImplicits.seqDerivedOrdering")

    val booleanOrdering = forClassFqn("scala.math.Ordering.BooleanOrdering")
    assertEquals(Set("scala.math.Ordering.Boolean"), booleanOrdering)

    val executionContext = forClassFqn("scala.concurrent.ExecutionContext")
    assertEquals(Set("scala.concurrent.ExecutionContext.Implicits.global"), executionContext)
  }

  def testImplicitConversion_ForClassFqn(): Unit = {
    def forClassFqn(fqn: String): Set[String] =
      ImplicitConversionIndex.forClassFqn(fqn, moduleWithLibraries)(getProject).flatMap(_.qualifiedNameOpt)

    val forScalaAny = forClassFqn(AnyFqn)
    assertContains(forScalaAny, "scala.math.Ordering.mkOrderingOps")
    assertContains(forScalaAny, "scala.math.Ordering.ExtraImplicits.infixOrderingOps")
    assertContains(forScalaAny, "scala.Predef.ArrowAssoc")
    assertContains(forScalaAny, "scala.Predef.StringFormat")

    val forJavaLangString = forClassFqn("java.lang.String")
    assertContains(forJavaLangString, "scala.LowPriorityImplicits.wrapString")

    //todo: aliased types should be found with original class names
    val forPredefString = forClassFqn("scala.Predef.String")
    assertContains(forPredefString, "scala.Predef.augmentString")

    val forMutableSeq = forClassFqn("scala.collection.mutable.Seq")
    assertContains(forMutableSeq, "scala.collection.convert.DecorateAsJava.mutableSeqAsJavaListConverter")

    val forJavaList = forClassFqn("java.util.List")
    assertContains(forJavaList, "scala.collection.convert.DecorateAsScala.asScalaBufferConverter")
  }

  def testImplicitConversion_AllConversions(): Unit = {
    val all = ImplicitConversionIndex.allConversions(moduleWithLibraries)(getProject).flatMap(_.qualifiedNameOpt).toSet
    assertEquals(
      """scala.Byte.byte2double
        |scala.Byte.byte2float
        |scala.Byte.byte2int
        |scala.Byte.byte2long
        |scala.Byte.byte2short
        |scala.Char.char2double
        |scala.Char.char2float
        |scala.Char.char2int
        |scala.Char.char2long
        |scala.Float.float2double
        |scala.Int.int2double
        |scala.Int.int2float
        |scala.Int.int2long
        |scala.Long.long2double
        |scala.Long.long2float
        |scala.LowPriorityImplicits.booleanWrapper
        |scala.LowPriorityImplicits.byteWrapper
        |scala.LowPriorityImplicits.charWrapper
        |scala.LowPriorityImplicits.doubleWrapper
        |scala.LowPriorityImplicits.floatWrapper
        |scala.LowPriorityImplicits.genericWrapArray
        |scala.LowPriorityImplicits.intWrapper
        |scala.LowPriorityImplicits.longWrapper
        |scala.LowPriorityImplicits.shortWrapper
        |scala.LowPriorityImplicits.unwrapString
        |scala.LowPriorityImplicits.wrapBooleanArray
        |scala.LowPriorityImplicits.wrapByteArray
        |scala.LowPriorityImplicits.wrapCharArray
        |scala.LowPriorityImplicits.wrapDoubleArray
        |scala.LowPriorityImplicits.wrapFloatArray
        |scala.LowPriorityImplicits.wrapIntArray
        |scala.LowPriorityImplicits.wrapLongArray
        |scala.LowPriorityImplicits.wrapRefArray
        |scala.LowPriorityImplicits.wrapShortArray
        |scala.LowPriorityImplicits.wrapString
        |scala.LowPriorityImplicits.wrapUnitArray
        |scala.Option.option2Iterable
        |scala.Predef.ArrowAssoc
        |scala.Predef.Boolean2boolean
        |scala.Predef.Byte2byte
        |scala.Predef.Character2char
        |scala.Predef.Double2double
        |scala.Predef.Ensuring
        |scala.Predef.Float2float
        |scala.Predef.Integer2int
        |scala.Predef.Long2long
        |scala.Predef.RichException
        |scala.Predef.Short2short
        |scala.Predef.StringFormat
        |scala.Predef.any2stringadd
        |scala.Predef.augmentString
        |scala.Predef.boolean2Boolean
        |scala.Predef.booleanArrayOps
        |scala.Predef.byte2Byte
        |scala.Predef.byteArrayOps
        |scala.Predef.char2Character
        |scala.Predef.charArrayOps
        |scala.Predef.double2Double
        |scala.Predef.doubleArrayOps
        |scala.Predef.float2Float
        |scala.Predef.floatArrayOps
        |scala.Predef.genericArrayOps
        |scala.Predef.int2Integer
        |scala.Predef.intArrayOps
        |scala.Predef.long2Long
        |scala.Predef.longArrayOps
        |scala.Predef.refArrayOps
        |scala.Predef.short2Short
        |scala.Predef.shortArrayOps
        |scala.Predef.tuple2ToZippedOps
        |scala.Predef.tuple3ToZippedOps
        |scala.Predef.unaugmentString
        |scala.Predef.unitArrayOps
        |scala.Short.short2double
        |scala.Short.short2float
        |scala.Short.short2int
        |scala.Short.short2long
        |scala.collection.Searching.search
        |scala.collection.TraversableOnce.MonadOps
        |scala.collection.TraversableOnce.alternateImplicit
        |scala.collection.TraversableOnce.flattenTraversableOnce
        |scala.collection.convert.DecorateAsJava.asJavaCollectionConverter
        |scala.collection.convert.DecorateAsJava.asJavaDictionaryConverter
        |scala.collection.convert.DecorateAsJava.asJavaEnumerationConverter
        |scala.collection.convert.DecorateAsJava.asJavaIterableConverter
        |scala.collection.convert.DecorateAsJava.asJavaIteratorConverter
        |scala.collection.convert.DecorateAsJava.bufferAsJavaListConverter
        |scala.collection.convert.DecorateAsJava.mapAsJavaConcurrentMapConverter
        |scala.collection.convert.DecorateAsJava.mapAsJavaMapConverter
        |scala.collection.convert.DecorateAsJava.mutableMapAsJavaMapConverter
        |scala.collection.convert.DecorateAsJava.mutableSeqAsJavaListConverter
        |scala.collection.convert.DecorateAsJava.mutableSetAsJavaSetConverter
        |scala.collection.convert.DecorateAsJava.seqAsJavaListConverter
        |scala.collection.convert.DecorateAsJava.setAsJavaSetConverter
        |scala.collection.convert.DecorateAsScala.asScalaBufferConverter
        |scala.collection.convert.DecorateAsScala.asScalaIteratorConverter
        |scala.collection.convert.DecorateAsScala.asScalaSetConverter
        |scala.collection.convert.DecorateAsScala.collectionAsScalaIterableConverter
        |scala.collection.convert.DecorateAsScala.dictionaryAsScalaMapConverter
        |scala.collection.convert.DecorateAsScala.enumerationAsScalaIteratorConverter
        |scala.collection.convert.DecorateAsScala.iterableAsScalaIterableConverter
        |scala.collection.convert.DecorateAsScala.mapAsScalaConcurrentMapConverter
        |scala.collection.convert.DecorateAsScala.mapAsScalaMapConverter
        |scala.collection.convert.DecorateAsScala.propertiesAsScalaMapConverter
        |scala.collection.convert.LowPriorityWrapAsJava.asJavaCollection
        |scala.collection.convert.LowPriorityWrapAsJava.asJavaDictionary
        |scala.collection.convert.LowPriorityWrapAsJava.asJavaEnumeration
        |scala.collection.convert.LowPriorityWrapAsJava.asJavaIterable
        |scala.collection.convert.LowPriorityWrapAsJava.asJavaIterator
        |scala.collection.convert.LowPriorityWrapAsJava.bufferAsJavaList
        |scala.collection.convert.LowPriorityWrapAsJava.mapAsJavaConcurrentMap
        |scala.collection.convert.LowPriorityWrapAsJava.mapAsJavaMap
        |scala.collection.convert.LowPriorityWrapAsJava.mutableMapAsJavaMap
        |scala.collection.convert.LowPriorityWrapAsJava.mutableSeqAsJavaList
        |scala.collection.convert.LowPriorityWrapAsJava.mutableSetAsJavaSet
        |scala.collection.convert.LowPriorityWrapAsJava.seqAsJavaList
        |scala.collection.convert.LowPriorityWrapAsJava.setAsJavaSet
        |scala.collection.convert.LowPriorityWrapAsScala.asScalaBuffer
        |scala.collection.convert.LowPriorityWrapAsScala.asScalaIterator
        |scala.collection.convert.LowPriorityWrapAsScala.asScalaSet
        |scala.collection.convert.LowPriorityWrapAsScala.collectionAsScalaIterable
        |scala.collection.convert.LowPriorityWrapAsScala.dictionaryAsScalaMap
        |scala.collection.convert.LowPriorityWrapAsScala.enumerationAsScalaIterator
        |scala.collection.convert.LowPriorityWrapAsScala.iterableAsScalaIterable
        |scala.collection.convert.LowPriorityWrapAsScala.mapAsScalaConcurrentMap
        |scala.collection.convert.LowPriorityWrapAsScala.mapAsScalaMap
        |scala.collection.convert.LowPriorityWrapAsScala.propertiesAsScalaMap
        |scala.collection.convert.ToJavaImplicits.`buffer AsJavaList`
        |scala.collection.convert.ToJavaImplicits.`collection asJava`
        |scala.collection.convert.ToJavaImplicits.`dictionary asJava`
        |scala.collection.convert.ToJavaImplicits.`enumeration asJava`
        |scala.collection.convert.ToJavaImplicits.`iterable asJava`
        |scala.collection.convert.ToJavaImplicits.`iterator asJava`
        |scala.collection.convert.ToJavaImplicits.`map AsJavaConcurrentMap`
        |scala.collection.convert.ToJavaImplicits.`map AsJavaMap`
        |scala.collection.convert.ToJavaImplicits.`mutableMap AsJavaMap`
        |scala.collection.convert.ToJavaImplicits.`mutableSeq AsJavaList`
        |scala.collection.convert.ToJavaImplicits.`mutableSet AsJavaSet`
        |scala.collection.convert.ToJavaImplicits.`seq AsJavaList`
        |scala.collection.convert.ToJavaImplicits.`set AsJavaSet`
        |scala.collection.convert.ToScalaImplicits.`collection AsScalaIterable`
        |scala.collection.convert.ToScalaImplicits.`dictionary AsScalaMap`
        |scala.collection.convert.ToScalaImplicits.`enumeration AsScalaIterator`
        |scala.collection.convert.ToScalaImplicits.`iterable AsScalaIterable`
        |scala.collection.convert.ToScalaImplicits.`iterator asScala`
        |scala.collection.convert.ToScalaImplicits.`list asScalaBuffer`
        |scala.collection.convert.ToScalaImplicits.`map AsScalaConcurrentMap`
        |scala.collection.convert.ToScalaImplicits.`map AsScala`
        |scala.collection.convert.ToScalaImplicits.`properties AsScalaMap`
        |scala.collection.convert.ToScalaImplicits.`set asScala`
        |scala.collection.convert.WrapAsJava.`deprecated asJavaCollection`
        |scala.collection.convert.WrapAsJava.`deprecated asJavaDictionary`
        |scala.collection.convert.WrapAsJava.`deprecated asJavaEnumeration`
        |scala.collection.convert.WrapAsJava.`deprecated asJavaIterable`
        |scala.collection.convert.WrapAsJava.`deprecated asJavaIterator`
        |scala.collection.convert.WrapAsJava.`deprecated bufferAsJavaList`
        |scala.collection.convert.WrapAsJava.`deprecated mapAsJavaConcurrentMap`
        |scala.collection.convert.WrapAsJava.`deprecated mapAsJavaMap`
        |scala.collection.convert.WrapAsJava.`deprecated mutableMapAsJavaMap`
        |scala.collection.convert.WrapAsJava.`deprecated mutableSeqAsJavaList`
        |scala.collection.convert.WrapAsJava.`deprecated mutableSetAsJavaSet`
        |scala.collection.convert.WrapAsJava.`deprecated seqAsJavaList`
        |scala.collection.convert.WrapAsJava.`deprecated setAsJavaSet`
        |scala.collection.convert.WrapAsScala.`deprecated asScalaBuffer`
        |scala.collection.convert.WrapAsScala.`deprecated asScalaIterator`
        |scala.collection.convert.WrapAsScala.`deprecated asScalaSet`
        |scala.collection.convert.WrapAsScala.`deprecated collectionAsScalaIterable`
        |scala.collection.convert.WrapAsScala.`deprecated dictionaryAsScalaMap`
        |scala.collection.convert.WrapAsScala.`deprecated enumerationAsScalaIterator`
        |scala.collection.convert.WrapAsScala.`deprecated iterableAsScalaIterable`
        |scala.collection.convert.WrapAsScala.`deprecated mapAsScalaConcurrentMap`
        |scala.collection.convert.WrapAsScala.`deprecated mapAsScalaMap`
        |scala.collection.convert.WrapAsScala.`deprecated propertiesAsScalaMap`
        |scala.collection.immutable.Stream.consWrapper
        |scala.collection.parallel.CollectionsHaveToParArray
        |scala.collection.parallel.ParIterableLike.builder2ops
        |scala.collection.parallel.ParIterableLike.delegatedSignalling2ops
        |scala.collection.parallel.ParIterableLike.task2ops
        |scala.collection.parallel.ParallelCollectionImplicits.factory2ops
        |scala.collection.parallel.ParallelCollectionImplicits.throwable2ops
        |scala.collection.parallel.ParallelCollectionImplicits.traversable2ops
        |scala.concurrent.JavaConversions.asExecutionContext
        |scala.concurrent.duration.DoubleMult
        |scala.concurrent.duration.DurationDouble
        |scala.concurrent.duration.DurationInt
        |scala.concurrent.duration.DurationLong
        |scala.concurrent.duration.IntMult
        |scala.concurrent.duration.LongMult
        |scala.concurrent.duration.durationToPair
        |scala.concurrent.duration.pairIntToDuration
        |scala.concurrent.duration.pairLongToDuration
        |scala.io.Codec.charset2codec
        |scala.io.Codec.decoder2codec
        |scala.io.Codec.string2codec
        |scala.math.BigDecimal.double2bigDecimal
        |scala.math.BigDecimal.int2bigDecimal
        |scala.math.BigDecimal.javaBigDecimal2bigDecimal
        |scala.math.BigDecimal.long2bigDecimal
        |scala.math.BigInt.int2bigInt
        |scala.math.BigInt.javaBigInteger2bigInt
        |scala.math.BigInt.long2bigInt
        |scala.math.Fractional.ExtraImplicits.infixFractionalOps
        |scala.math.Fractional.mkNumericOps
        |scala.math.Integral.ExtraImplicits.infixIntegralOps
        |scala.math.Integral.mkNumericOps
        |scala.math.Numeric.ExtraImplicits.infixNumericOps
        |scala.math.Numeric.mkNumericOps
        |scala.math.Ordered.orderingToOrdered
        |scala.math.Ordering.ExtraImplicits.infixOrderingOps
        |scala.math.Ordering.mkOrderingOps
        |scala.reflect.macros.contexts.Aliases.RichOpenImplicit
        |scala.reflect.reify.phases.Calculate.RichCalculateSymbol
        |scala.reflect.reify.phases.Calculate.RichCalculateType
        |scala.runtime.ZippedTraversable2.zippedTraversable2ToTraversable
        |scala.runtime.ZippedTraversable3.zippedTraversable3ToTraversable
        |scala.sys.BooleanProp.booleanPropAsBoolean
        |scala.sys.SystemProperties.systemPropertiesToCompanion
        |scala.sys.process.ProcessImplicits.builderToProcess
        |scala.sys.process.ProcessImplicits.buildersToProcess
        |scala.sys.process.ProcessImplicits.fileToProcess
        |scala.sys.process.ProcessImplicits.stringSeqToProcess
        |scala.sys.process.ProcessImplicits.stringToProcess
        |scala.sys.process.ProcessImplicits.urlToProcess
        |scala.tools.cmd.Instance.optionMagicAdditions
        |scala.tools.cmd.Reference.optionMagicAdditions
        |scala.tools.cmd.Spec.optionMagicAdditions
        |scala.tools.nsc.ast.TreeBrowsers.TypePrinter.view
        |scala.tools.nsc.ast.TreeDSL.CODE.mkTreeFromSelectStart
        |scala.tools.nsc.ast.TreeDSL.CODE.mkTreeMethods
        |scala.tools.nsc.ast.TreeDSL.CODE.mkTreeMethodsFromSelectStart
        |scala.tools.nsc.ast.TreeDSL.CODE.mkTreeMethodsFromSymbol
        |scala.tools.nsc.ast.parser.Cbuf.StringBuilderOps
        |scala.tools.nsc.backend.jvm.BCodeIdiomatic.InsnIterInsnList
        |scala.tools.nsc.backend.jvm.BCodeIdiomatic.InsnIterMethodNode
        |scala.tools.nsc.backend.jvm.BackendReporting.RightBiasedEither
        |scala.tools.nsc.backend.jvm.opt.BytecodeUtils.AnalyzerExtensions
        |scala.tools.nsc.backend.jvm.opt.BytecodeUtils.FrameExtensions
        |scala.tools.nsc.classpath.ClassPathEntries.entry2Tuple
        |scala.tools.nsc.classpath.FileUtils.AbstractFileOps
        |scala.tools.nsc.classpath.FileUtils.FileOps
        |scala.tools.nsc.interactive.Global.addOnTypeError
        |scala.tools.nsc.interactive.Pickler.TildeDecorator
        |scala.tools.nsc.interpreter.ILoop.loopToInterpreter
        |scala.tools.nsc.interpreter.IMain.ReplTypeOps
        |scala.tools.nsc.interpreter.IMainOps
        |scala.tools.nsc.interpreter.LoopCommands.Result.resultFromString
        |scala.tools.nsc.interpreter.LoopCommands.Result.resultFromUnit
        |scala.tools.nsc.interpreter.NamedParamCreator.tuple
        |scala.tools.nsc.interpreter.Phased.PhaseName.apply
        |scala.tools.nsc.interpreter.Phased.phaseEnumToPhase
        |scala.tools.nsc.interpreter.Power.Implicits1.replPrinting
        |scala.tools.nsc.interpreter.Power.Implicits2.replEnhancedStrings
        |scala.tools.nsc.interpreter.Power.Implicits2.replEnhancedURLs
        |scala.tools.nsc.interpreter.Power.Implicits2.replInputStream
        |scala.tools.nsc.interpreter.Power.Implicits2.replInternalInfo
        |scala.tools.nsc.interpreter.Power.Implicits2.replMultiPrinting
        |scala.tools.nsc.interpreter.Power.Implicits2.replTypeApplication
        |scala.tools.nsc.interpreter.SimpleMath.DivRem
        |scala.tools.nsc.interpreter.StdReplVals.ReplImplicits.mkCompilerTypeFromTag
        |scala.tools.nsc.interpreter.`smart stringifier`
        |scala.tools.nsc.interpreter.`try lastly`
        |scala.tools.nsc.interpreter.enrichAnyRefWithTap
        |scala.tools.nsc.interpreter.enrichClass
        |scala.tools.nsc.interpreter.javaCharSeqCollectionToScala
        |scala.tools.nsc.interpreter.session.charSequenceFix
        |scala.tools.nsc.io.enrichManifest
        |scala.tools.nsc.javac.JavaParsers.JavaParser.i2p
        |scala.tools.nsc.javac.JavaParsers.JavaUnitParser.i2p
        |scala.tools.nsc.javac.JavaScanners.AbstractJavaScanner.g2p
        |scala.tools.nsc.javac.JavaScanners.JavaUnitScanner.g2p
        |scala.tools.nsc.settings.MutableSettings.installEnableSettings
        |scala.tools.nsc.util.StackTraceOps
        |scala.tools.reflect.Eval
        |scala.tools.reflect.ToolBox
        |scala.tools.util.PathResolver.AsLines
        |scala.tools.util.PathResolver.MkLines
        |scala.util.Either.MergeableEither
        |scala.util.Random.javaRandomToRandom
        |scala.util.control.Exception.throwableSubtypeToCatcher""".stripMargin.replace("\r", ""),
      all.toSeq.sorted.mkString("\n")
    )
  }
}
