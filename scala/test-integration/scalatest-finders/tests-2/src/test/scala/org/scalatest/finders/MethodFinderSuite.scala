/*
 * Copyright 2001-2008 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalatest.finders

import org.scalatest.Suite

import org.scalatest.Matchers._

class MethodFinderSuite extends FinderSuite {

  test("MethodFinder should find test name for tests written in test suite that extends org.scalatest.Suite") {
    class TestingSuite extends Suite {
      def testMethod1(aParam: String) {

      }
      def testMethod2() {

      }
      def testMethod3() {
        def testNested() {

        }
      }
    }

    val suiteClass = classOf[TestingSuite]
    val suiteClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingSuite")
    val suiteConstructor = new ConstructorBlock(suiteClass.getName, suiteClassDef, Array.empty)
    val testMethod1 = new MethodDefinition(suiteClass.getName, suiteConstructor, Array.empty, "testMethod1", "java.lang.String")
    val testMethod2 = new MethodDefinition(suiteClass.getName, suiteConstructor, Array.empty, "testMethod2")
    val testMethod3 = new MethodDefinition(suiteClass.getName, suiteConstructor, Array.empty, "testMethod3")
    val testNested = new MethodDefinition(suiteClass.getName, testMethod3, Array.empty, "testNested")

    val finders = LocationUtils.getFinders(suiteClass)

    withClue("org.scalatest.Suite finders"){
      finders should have size 1
    }

    val finder = finders.get(0)
    assertResult(classOf[MethodFinder], "Suite that uses org.scalatest.Suite should use MethodFinder.")(
      finder.getClass
    )

    val selectionMethod1 = finder.find(testMethod1)
    assertResult(null)(selectionMethod1)

    val selectionMethod2 = finder.find(testMethod2)
    expectSelection(selectionMethod2, suiteClass.getName, suiteClass.getName + ".testMethod2", Array("testMethod2"))

    val selectionMethod3 = finder.find(testMethod3)
    expectSelection(selectionMethod3, suiteClass.getName, suiteClass.getName + ".testMethod3", Array("testMethod3"))

    val selectionNested = finder.find(testNested)
    expectSelection(selectionNested, suiteClass.getName, suiteClass.getName + ".testMethod3", Array("testMethod3"))
  }

}