package org.jetbrains.plugins.scala
package performance.highlighting


import base.ScalaFixtureTestCase
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import com.intellij.openapi.vfs.{VfsUtil, LocalFileSystem}
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import com.intellij.openapi.projectRoots.JavaSdk
import lang.psi.impl.toplevel.synthetic.SyntheticClasses
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, LightCodeInsightFixtureTestCase}
import util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.08.2009
 */

class HighlightingPerformanceTest extends ScalaFixtureTestCase {
  def testPerformance {
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
        td { border-bottom: 1px solid #acacac;""" + "\"\"\"" + """}
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
    val file = PsiFileFactory.getInstance(myFixture.getProject).
            createFileFromText("dummy." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(),
      ScalaFileType.SCALA_LANGUAGE, text, true, false)
    TestUtils.assertTiming("Failed highlighting performance test", 3000,
      new Runnable {
        def run: Unit = {
          try {
            myFixture.testHighlighting(false, false, false, file.getVirtualFile)
          }
          catch {
            case e: RuntimeException =>
          }
        }
      })
  }
}