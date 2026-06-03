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

import org.scalatest.FunSuite
import org.scalatest.PropSpec

class FunctionFinderSuite extends FinderSuite {
  test("FunctionFinder should find test name for tests written in test suite that extends org.scalatest.FunSuite") {
    class TestingFunSuite extends FunSuite {
      test("test 1") {
        
      }
      test("test 2") {
        test("nested") {
          
        }
      }
      test("test 3") {
        
      }
    }
    
    val suiteClass = classOf[TestingFunSuite]
    val suiteClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFunSuite")
    val suiteConstructor = new ConstructorBlock(suiteClass.getName, suiteClassDef, Array.empty)
    val test1 = new MethodInvocation(suiteClass.getName, null, suiteConstructor, Array.empty, "test", new StringLiteral(suiteClass.getName, null, "test 1"))
    val test2 = new MethodInvocation(suiteClass.getName, null, suiteConstructor, Array.empty, "test", new StringLiteral(suiteClass.getName, null, "test 2"))
    val nested = new MethodInvocation(suiteClass.getName, null, test2, Array.empty, "test", new StringLiteral(suiteClass.getName, null, "nested"))
    val test3 = new MethodInvocation(suiteClass.getName, null, suiteConstructor, Array.empty, "test", new StringLiteral(suiteClass.getName, null, "test 3"))
    
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FunSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FunSuiteFinder], "Suite that uses org.scalatest.FunSuite should use FunSuiteFinder.")
    val test1Selection = finder.find(test1)
    expectSelection(test1Selection, suiteClass.getName, suiteClass.getName + ": \"test 1\"", Array("test 1"))
    val test2Selection = finder.find(test2)
    expectSelection(test2Selection, suiteClass.getName, suiteClass.getName + ": \"test 2\"", Array("test 2"))
    val nestedSelection = finder.find(nested)
    expectSelection(nestedSelection, suiteClass.getName, suiteClass.getName + ": \"test 2\"", Array("test 2"))
    val test3Selection = finder.find(test3)
    expectSelection(test3Selection, suiteClass.getName, suiteClass.getName + ": \"test 3\"", Array("test 3"))
  }
  
  test("FunctionFinder should find test name for tests written in test suite that extends org.scalatest.PropSpec") {
    class TestingPropSpec extends PropSpec {
      property("Fraction constructor normalizes numerator and denominator.") {
        
      }
      
      property("Fraction constructor throws IAE on bad data.") {
        println("nested")
      }
    }
    
    val suiteClass = classOf[TestingPropSpec]
    val suiteClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingPropSpec")
    val suiteConstructor = new ConstructorBlock(suiteClass.getName, suiteClassDef, Array.empty)
    val prop1 = new MethodInvocation(suiteClass.getName, null, suiteConstructor, Array.empty, "property", new StringLiteral(suiteClass.getName, null, "Fraction constructor normalizes numerator and denominator."))
    val prop2 = new MethodInvocation(suiteClass.getName, null, suiteConstructor, Array.empty, "property", new StringLiteral(suiteClass.getName, null, "Fraction constructor throws IAE on bad data."))
    val nested = new MethodInvocation(suiteClass.getName, null, prop2, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested"))
    
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.PropSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[PropSpecFinder], "Suite that uses org.scalatest.PropSpec should use PropSpecFinder.")
    val prop1Selection = finder.find(prop1)
    expectSelection(prop1Selection, suiteClass.getName, suiteClass.getName + ": \"Fraction constructor normalizes numerator and denominator.\"", Array("Fraction constructor normalizes numerator and denominator."))
    val prop2Selection = finder.find(prop2)
    expectSelection(prop2Selection, suiteClass.getName, suiteClass.getName + ": \"Fraction constructor throws IAE on bad data.\"", Array("Fraction constructor throws IAE on bad data."))
    val nestedSelection = finder.find(nested)
    expectSelection(nestedSelection, suiteClass.getName, suiteClass.getName + ": \"Fraction constructor throws IAE on bad data.\"", Array("Fraction constructor throws IAE on bad data."))
  }
}