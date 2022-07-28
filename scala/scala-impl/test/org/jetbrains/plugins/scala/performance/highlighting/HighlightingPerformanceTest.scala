package org.jetbrains.plugins.scala
package performance.highlighting

import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.junit.experimental.categories.Category

import scala.util.Try

@Category(Array(classOf[SlowTests]))
class HighlightingPerformanceTest extends ScalaFixtureTestCase {
  def doTest(text: String, TIMEOUT: Int): Unit = {

    PlatformTestUtil.assertTiming("Running highlighting performance test", TIMEOUT,
      () => Try {
        val file = PsiFileFactory.getInstance(myFixture.getProject)
          .createFileFromText("dummy.scala", ScalaLanguage.INSTANCE, text, true, false)
        myFixture.allowTreeAccessForAllFiles()
        myFixture.testHighlighting(false, false, false, file.getVirtualFile)
      }
    )
  }

  def testPerformance(): Unit = {
    val text = """
object addressbook {

  case class Person(name: String, age: Int)

  /** An AddressBook takes a variable number of arguments
   *  which are accessed as a Sequence
   */
  class AddressBook(a: Person*) {
    private val people: List[Person] = a.toList

    /** Serialize to XHTML. Scala supports XML literals
     *  which may contain Scala expressions between braces,
     *  which are replaced by their evaluation
     */
    def toXHTML =
      <table cellpadding="2" cellspacing="0">
        <tr>
          <th>Last Name</th>
          <th>First Name</th>
        </tr>
        { for (val p <- people) yield
            <tr>
              <td> { p.name } </td>
              <td> { p.age.toString() } </td>
            </tr>
        }
      </table>;
  }

  /** We introduce CSS using raw strings (between triple
   *  quotes). Raw strings may contain newlines and special
   *  characters (like \) are not interpreted.
   */
  val header =
    <head>
      <title>
        { "My Address Book" }
      </title>
      <style type="text/css"> {
     """ + "\"\"\"" + """table { border-right: 1px solid #cccccc; }
        th { background-color: #cccccc; }
        td { border-left: 1px solid #acacac; }
        td { border-bottom: 1px solid #acacac; }""" + "\"\"\"" + """}
      </style>
    </head>;

  val people = new AddressBook(
    Person("Tom", 20),
    Person("Bob", 22),
    Person("James", 19));

  val page =
    <html>
      { header }
      <body>
       { people.toXHTML }
      </body>
    </html>;

  def main(args: Array[String]) {
    println(page)
  }
}
"""
    val TIMEOUT: Int = 15000
    doTest(text, TIMEOUT)
  }

  def testLotsOfArguments(): Unit = {
    val text =
      """
        |val x: List[Int] = List(79,59,12,2,79,35,8,28,20,2,3,68,8,9,68,45,0,12,9,67,68,4,7,5,23,27,1,21,79,85,78,79,85,71,38,10,71,27
        |  ,12,2,79,6,2,8,13,9,1,13,9,8,68,19,7,1,71,56,11,21,11,68,6,3,22,2,14,0,30,79,1,31,6,23,19,10,0,73,79,44,2,79,19,6,28,68,
        |  16,6,16,15,79,35,8,11,72,71,14,10,3,79,12,2,79,19,6,28,68,32,0,0,73,79,86,71,39,1,71,24,5,20,79,13,9,79,16,15,10,68,5,
        |  10,3,14,1,10,14,1,3,71,24,13,19,7,68,32,0,0,73,79,87,71,39,1,71,12,22,2,14,16,2,11,68,2,25,1,21,22,16,15,6,10,0,79,16,
        |  15,10,22,2,79,13,20,65,68,41,0,16,15,6,10,0,79,1,31,6,23,19,28,68,19,7,5,19,79,12,2,79,0,14,11,10,64,27,68,10,14,15,2,
        |  65,68,83,79,40,14,9,1,71,6,16,20,10,8,1,79,19,6,28,68,14,1,68,15,6,9,75,79,5,9,11,68,19,7,13,20,79,8,14,9,1,71,8,13,17,
        |  10,23,71,3,13,0,7,16,71,27,11,71,10,18,2,29,29,8,1,1,73,79,81,71,59,12,2,79,8,14,8,12,19,79,23,15,6,10,2,28,68,19,7,22,
        |  8,26,3,15,79,16,15,10,68,3,14,22,12,1,1,20,28,72,71,14,10,3,79,16,15,10,68,3,14,22,12,1,1,20,28,68,4,14,10,71,1,1,17,10,
        |  22,71,10,28,19,6,10,0,26,13,20,7,68,14,27,74,71,89,68,32,0,0,71,28,1,9,27,68,45,0,12,9,79,16,15,10,68,37,14,20,19,6,23,19,
        |  79,83,71,27,11,71,27,1,11,3,68,2,25,1,21,22,11,9,10,68,6,13,11,18,27,68,19,7,1,71,3,13,0,7,16,71,28,11,71,27,12,6,27,68,2
        |  ,25,1,21,22,11,9,10,68,10,6,3,15,27,68,5,10,8,14,10,18,2,79,6,2,12,5,18,28,1,71,0,2,71,7,13,20,79,16,2,28,16,14,2,11,9,22,
        |  74,71,87,68,45,0,12,9,79,12,14,2,23,2,3,2,71,24,5,20,79,10,8,27,68,19,7,1,71,3,13,0,7,16,92,79,12,2,79,19,6,28,68,8,1,8,30,
        |  79,5,71,24,13,19,1,1,20,28,68,19,0,68,19,7,1,71,3,13,0,7,16,73,79,93,71,59,12,2,79,11,9,10,68,16,7,11,71,6,23,71,27,12,2,
        |  79,16,21,26,1,71,3,13,0,7,16,75,79,19,15,0,68,0,6,18,2,28,68,11,6,3,15,27,68,19,0,68,2,25,1,21,22,11,9,10,72,71,24,5,20,
        |  79,3,8,6,10,0,79,16,8,79,7,8,2,1,71,6,10,19,0,68,19,7,1,71,24,11,21,3,0,73,79,85,87,79,38,18,27,68,6,3,16,15,0,17,0,7,68,
        |  19,7,1,71,24,11,21,3,0,71,24,5,20,79,9,6,11,1,71,27,12,21,0,17,0,7,68,15,6,9,75,79,16,15,10,68,16,0,22,11,11,68,3,6,0,9
        |  ,72,16,71,29,1,4,0,3,9,6,30,2,79,12,14,2,68,16,7,1,9,79,12,2,79,7,6,2,1,73,79,85,86,79,33,17,10,10,71,6,10,71,7,13,20,79,
        |  11,16,1,68,11,14,10,3,79,5,9,11,68,6,2,11,9,8,68,15,6,23,71,0,19,9,79,20,2,0,20,11,10,72,71,7,1,71,24,5,20,79,10,8,27,
        |  68,6,12,7,2,31,16,2,11,74,71,94,86,71,45,17,19,79,16,8,79,5,11,3,68,16,7,11,71,13,1,11,6,1,17,10,0,71,7,13,10,79,5,9,
        |  11,68,6,12,7,2,31,16,2,11,68,15,6,9,75,79,12,2,79,3,6,25,1,71,27,12,2,79,22,14,8,12,19,79,16,8,79,6,2,12,11,10,10,68,4,
        |  7,13,11,11,22,2,1,68,8,9,68,32,0,0,73,79,85,84,79,48,15,10,29,71,14,22,2,79,22,2,13,11,21,1,69,71,59,12,14,28,68,14,28,
        |  68,9,0,16,71,14,68,23,7,29,20,6,7,6,3,68,5,6,22,19,7,68,21,10,23,18,3,16,14,1,3,71,9,22,8,2,68,15,26,9,6,1,68,23,14,23,
        |  20,6,11,9,79,11,21,79,20,11,14,10,75,79,16,15,6,23,71,29,1,5,6,22,19,7,68,4,0,9,2,28,68,1,29,11,10,79,35,8,11,74,86,91,
        |  68,52,0,68,19,7,1,71,56,11,21,11,68,5,10,7,6,2,1,71,7,17,10,14,10,71,14,10,3,79,8,14,25,1,3,79,12,2,29,1,71,0,10,71,10,
        |  5,21,27,12,71,14,9,8,1,3,71,26,23,73,79,44,2,79,19,6,28,68,1,26,8,11,79,11,1,79,17,9,9,5,14,3,13,9,8,68,11,0,18,2,79,5,
        |  9,11,68,1,14,13,19,7,2,18,3,10,2,28,23,73,79,37,9,11,68,16,10,68,15,14,18,2,79,23,2,10,10,71,7,13,20,79,3,11,0,22,30,67
        |  ,68,19,7,1,71,8,8,8,29,29,71,0,2,71,27,12,2,79,11,9,3,29,71,60,11,9,79,11,1,79,16,15,10,68,33,14,16,15,10,22,73)
      """.stripMargin
    val TIMEOUT = 13000

    doTest(text, TIMEOUT)
  }

  //SCL-18276
  def testExcessiveNamedArgumentsOfApply(): Unit = {
    doTest(
      """
        |object Raw {
        |
        |  final case class Boo2020_10_05(
        |                                          salesRegion: String,
        |                                          region: String,
        |                                          market: String,
        |                                          country: String,
        |                                          regionalOrigin: String,
        |                                          strategicGroup: String,
        |                                          designParent: String,
        |                                          salesParent: String,
        |                                          salesGroup: String,
        |                                          salesBrand: String,
        |                                          salesNameplate: String,
        |                                          globalNameplate: String,
        |                                          platform: String,
        |                                          program: String,
        |                                          actualDate: String,
        |                                          GVWRating: String,
        |                                          globalIndustrySegment: String,
        |                                          globalIndustryRegion: String,
        |                                          productionType: String,
        |                                          carTruck: String,
        |                                          off_Type: String,
        |                                          globalSalesSegment: String,
        |                                          globalSalesSubSegment: String,
        |                                          globalSalesPriceClass: String,
        |                                          regionalSalesSegment: String,
        |                                          regionalSubSegment: String,
        |                                          regionalSalesPriceClass: String,
        |                                          architecture: String,
        |                                          mnemonicNamplateCountry: String,
        |                                          monthsInMarket: Int,
        |                                          vehicleLifeCycleinMonth: Int,
        |                                          year2010: Int,
        |                                          year2011: Int,
        |                                          year2012: Int,
        |                                          year2013: Int,
        |                                          year2014: Int,
        |                                          year2015: Int,
        |                                          year2016: Int,
        |                                          year2017: Int,
        |                                          year2018: Int,
        |                                          year2019: Int,
        |                                          year2020: Int,
        |                                          year2021: Int,
        |                                          year2022: Int,
        |                                          year2023: Int,
        |                                          year2024: Int,
        |                                          year2025: Int,
        |                                          year2026: Int,
        |                                          year2027: Int,
        |                                          year2028: Int,
        |                                          year2029: Int,
        |                                          year2030: Int,
        |                                          year2031: Int,
        |                                          year2032: Int
        |                                        )
        |
        |}
        |
        |object Clean {
        |
        |  val Boo2020_10_05: Raw.Boo2020_10_05.type = Raw.Boo2020_10_05
        |  type Boo2020_10_05 = Raw.Boo2020_10_05
        |
        |}
        |
        |object Boo2020StubsProvider {
        |
        |  import Clean.Boo2020_10_05
        |
        |  final def clean2020_10_05(): Boo2020_10_05 = {
        |    Boo2020_10_05(
        |      <error descr="Cannot resolve symbol salesRegion">salesRegion</error> = "A",
        |      <error descr="Cannot resolve symbol region">region</error> = "A",
        |      <error descr="Cannot resolve symbol market">market</error> = "A",
        |      <error descr="Cannot resolve symbol country">country</error> = "A",
        |      <error descr="Cannot resolve symbol regionalOrigin">regionalOrigin</error> = "A",
        |      <error descr="Cannot resolve symbol actualDate">actualDate</error> = "A",
        |      <error descr="Cannot resolve symbol GVWRating">GVWRating</error> = "A",
        |      <error descr="Cannot resolve symbol globalIndustrySegment">globalIndustrySegment</error> = "A",
        |      <error descr="Cannot resolve symbol globalIndustryRegion">globalIndustryRegion</error> = "A",
        |      <error descr="Cannot resolve symbol productionType">productionType</error> = "A",
        |      <error descr="Cannot resolve symbol carTruck">carTruck</error> = "A",
        |      <error descr="Cannot resolve symbol off_Type">off_Type</error> = "A",
        |      <error descr="Cannot resolve symbol globalSalesSegment">globalSalesSegment</error> = "A",
        |      <error descr="Cannot resolve symbol globalSalesSubSegment">globalSalesSubSegment</error> = "A",
        |      <error descr="Cannot resolve symbol globalSalesPriceClass">globalSalesPriceClass</error> = "A",
        |      <error descr="Cannot resolve symbol regionalSalesSegment">regionalSalesSegment</error> = "A",
        |      <error descr="Cannot resolve symbol regionalSubSegment">regionalSubSegment</error> = "A",
        |      <error descr="Cannot resolve symbol regionalSalesPriceClass">regionalSalesPriceClass</error> = "A",
        |      <error descr="Cannot resolve symbol architecture">architecture</error> = "A",
        |      <error descr="Cannot resolve symbol mnemonicNamplateCountry">mnemonicNamplateCountry</error> = "A",
        |      <error descr="Cannot resolve symbol monthsInMarket">monthsInMarket</error> = 1,
        |      <error descr="Cannot resolve symbol vehicleLifeCycleinMonth">vehicleLifeCycleinMonth</error> = 1,
        |      <error descr="Cannot resolve symbol strategicGroup">strategicGroup</error> = "A",
        |      <error descr="Cannot resolve symbol designParent">designParent</error> = "A",
        |      <error descr="Cannot resolve symbol salesParent">salesParent</error> = "A",
        |      <error descr="Cannot resolve symbol salesGroup">salesGroup</error> = "A",
        |      <error descr="Cannot resolve symbol salesBrand">salesBrand</error> = "A",
        |      <error descr="Cannot resolve symbol salesNameplate">salesNameplate</error> = "A",
        |      <error descr="Cannot resolve symbol globalNameplate">globalNameplate</error> = "A",
        |      <error descr="Cannot resolve symbol platform">platform</error> = "A",
        |      <error descr="Cannot resolve symbol program">program</error> = "A",
        |      <error descr="Cannot resolve symbol year2000">year2000</error> = 0,
        |      <error descr="Cannot resolve symbol year2001">year2001</error> = 0,
        |      <error descr="Cannot resolve symbol year2002">year2002</error> = 0,
        |      <error descr="Cannot resolve symbol year2003">year2003</error> = 0,
        |      <error descr="Cannot resolve symbol year2004">year2004</error> = 0,
        |      <error descr="Cannot resolve symbol year2005">year2005</error> = 0,
        |      <error descr="Cannot resolve symbol year2006">year2006</error> = 0,
        |      <error descr="Cannot resolve symbol year2007">year2007</error> = 0,
        |      <error descr="Cannot resolve symbol year2008">year2008</error> = 0,
        |      <error descr="Cannot resolve symbol year2009">year2009</error> = 0,
        |      <error descr="Cannot resolve symbol year2010">year2010</error> = 0,
        |      <error descr="Cannot resolve symbol year2011">year2011</error> = 0,
        |      <error descr="Cannot resolve symbol year2012">year2012</error> = 0,
        |      <error descr="Cannot resolve symbol year2013">year2013</error> = 0,
        |      <error descr="Cannot resolve symbol year2014">year2014</error> = 0,
        |      <error descr="Cannot resolve symbol year2015">year2015</error> = 0,
        |      <error descr="Cannot resolve symbol year2016">year2016</error> = 0,
        |      <error descr="Cannot resolve symbol year2017">year2017</error> = 0,
        |      <error descr="Cannot resolve symbol year2018">year2018</error> = 0,
        |      <error descr="Cannot resolve symbol year2019">year2019</error> = 0,
        |      <error descr="Cannot resolve symbol year2020">year2020</error> = 1,
        |      <error descr="Cannot resolve symbol year2021">year2021</error> = 1,
        |      <error descr="Cannot resolve symbol year2022">year2022</error> = 1,
        |      <error descr="Cannot resolve symbol year2023">year2023</error> = 0,
        |      <error descr="Cannot resolve symbol year2024">year2024</error> = 0,
        |      <error descr="Cannot resolve symbol year2025">year2025</error> = 0,
        |      <error descr="Cannot resolve symbol year2026">year2026</error> = 0,
        |      <error descr="Cannot resolve symbol year2027">year2027</error> = 0,
        |      <error descr="Cannot resolve symbol year2028">year2028</error> = 0,
        |      <error descr="Cannot resolve symbol year2029">year2029</error> = 0,
        |      <error descr="Cannot resolve symbol year2030">year2030</error> = 0,
        |      <error descr="Cannot resolve symbol year2031">year2031</error> = 0,
        |      <error descr="Cannot resolve symbol year2032">year2032</error> = 0
        |    )
        |  }
        |}""".stripMargin, 5000)
  }
}