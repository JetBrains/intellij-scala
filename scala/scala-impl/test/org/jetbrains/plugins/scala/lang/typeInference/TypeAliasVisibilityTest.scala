package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class TypeAliasVisibilityTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL13978(): Unit = checkTextHasNoErrors(
    """
      |trait Base {
      |  type Self
      |  def foo: Self = ???
      |}
      |class Good extends Base {
      |  type Self = Good
      |}
      |class Bad extends Base {
      |  protected type Self = Bad
      |}
      |val g1: Good = new Good().foo // ok
      |val g2: Good = g1.foo // ok
      |val b1: Bad = new Bad().foo // ok
      |val b2: Bad = b1.foo // error: Expression of type b1.Self doesn't conform to expected type Bad
    """.stripMargin
  )

  def testSCL13103(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.language.higherKinds
       |
       |sealed trait Context
       |object Local extends Context
       |object Editing extends Context
       |
       |abstract class DataModel[C <: Context](val ctx: C)
       |
       |trait DataModelAccessor {
       |  type DM[X <: Context] <: DataModel[X]
       |  protected def buildDataModel[C <: Context](ctx: C): DM[C]
       |  final val dataModel: DM[Local.type] = buildDataModel(Local)
       |}
       |
       |trait DataModelAccessorProvider {
       |  protected type DMA <: DataModelAccessor
       |  final val dataModelAccessor: DMA = buildDataModelAccessor
       |  protected def buildDataModelAccessor: DMA
       |}
       |
       |class ApplicationDataModel[C <: Context](ctx: C) extends DataModel(ctx) {
       |  val foo: Int = 42
       |}
       |
       |class ApplicationDataModelAccessor extends DataModelAccessor {
       |  override type DM[X <: Context] = ApplicationDataModel[X]
       |  override def buildDataModel[C <: Context](ctx: C): ApplicationDataModel[C] = new ApplicationDataModel(ctx)
       |}
       |
       |class ApplicationDataModelAccessorProvider extends DataModelAccessorProvider {
       |  override protected type DMA = ApplicationDataModelAccessor
       |  override protected def buildDataModelAccessor: ApplicationDataModelAccessor = new ApplicationDataModelAccessor
       |}
       |
       |class Test(app: ApplicationDataModelAccessorProvider) {
       |  val x: ApplicationDataModel[Local.type] = app.dataModelAccessor.dataModel
       |}
       |""".stripMargin
  )

  def testSCL9178(): Unit = {
    val text =
      """
        |trait Example {
        |  protected type EXAMPLE <: Example
        |  def example() : EXAMPLE
        |}
        |
        |trait ExtendedExample extends Example {
        |  final protected type EXAMPLE = ExtendedExample
        |  def myDef() : Unit
        |}
        |
        |trait Something extends Example {
        |  val exampleVal = example()
        |  def falseErrorHere() = {
        |    val extendedSomethingInstance = new ExtendedSomethingClass
        |    extendedSomethingInstance.exampleVal.myDef  //<-- Cannot resolve symbol myDef
        |  }
        |}
        |
        |trait ExtendedSomething extends Something with ExtendedExample {
        |}
        |
        |class ExtendedSomethingClass extends ExtendedSomething {
        |  def myDef() : Unit = {}
        |  def example() = new ExtendedSomethingClass
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
