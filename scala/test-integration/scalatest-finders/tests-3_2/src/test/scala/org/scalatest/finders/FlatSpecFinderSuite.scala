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

import org.scalatest.flatspec.AnyFlatSpec

class FlatSpecFinderSuite extends FinderSuite {

  test("FlatSpecFinder should find test name for tests written in test suite that extends org.scalatest.FlatSpec, using behavior of way and 'should'.") {
    class TestingFlatSpec1 extends AnyFlatSpec {
      behavior of "A Stack"

      it should "pop values in last-in-first-out order" in { 
        println("nested")
      }

      it should "throw NoSuchElementException if an empty stack is popped" in { 
        println("nested")
      }
      
      behavior of "A List"
      
      it should "put values in the sequence they are put in" in {
        
      }
      
      it should "throw ArrayIndexOutOfBoundsException when invalid index is applied" in {
        
      }
    }
    val suiteClass = classOf[TestingFlatSpec1]
    val spec1ClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFlatSpec1")
    val spec1Constructor = new ConstructorBlock(suiteClass.getName, spec1ClassDef, Array.empty)
    
    val spec1BehaviorOf1 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "behaviour"), spec1Constructor, Array.empty, "of", new StringLiteral(suiteClass.getName, null, "A Stack"))
    val spec1ItShould1 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "pop values in last-in-first-out order"))
    val spec1ItShouldIn1 = new MethodInvocation(suiteClass.getName, spec1ItShould1, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec1Nested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), spec1ItShouldIn1, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested"))
    val spec1ItShould2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "throw NoSuchElementException if an empty stack is popped"))
    val spec1ItShouldIn2 = new MethodInvocation(suiteClass.getName, spec1ItShould2, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val spec1BehaviorOf2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "behaviour"), spec1Constructor, Array.empty, "of", new StringLiteral(suiteClass.getName, null, "A List"))
    val spec1ItShould3 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "put values in the sequence they are put in"))
    val spec1ItShouldIn3 = new MethodInvocation(suiteClass.getName, spec1ItShould3, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec1ItShould4 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    val spec1ItShouldIn4 = new MethodInvocation(suiteClass.getName, spec1ItShould4, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    List[AstNode](spec1Constructor, spec1BehaviorOf1, spec1ItShould1, spec1ItShouldIn1, spec1ItShould2, spec1ItShouldIn2, 
                  spec1BehaviorOf2, spec1ItShould3, spec1ItShouldIn3, spec1ItShould4, spec1ItShouldIn4).foreach(_.parent)
    
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FlatSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FlatSpecFinder], "Suite that uses org.scalatest.FlatSpec should use FlatSpecFinder.")
    
    val spec1ConstructorSelection = finder.find(spec1Constructor)
    expectSelection(spec1ConstructorSelection, suiteClass.getName, suiteClass.getName, Array("A Stack should pop values in last-in-first-out order", "A Stack should throw NoSuchElementException if an empty stack is popped", "A List should put values in the sequence they are put in", "A List should throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec1BehaviorOf1Selection = finder.find(spec1BehaviorOf1)
    expectSelection(spec1BehaviorOf1Selection, suiteClass.getName, "A Stack", Array("A Stack should pop values in last-in-first-out order", "A Stack should throw NoSuchElementException if an empty stack is popped"))
    
    val spec1ItShouldIn1Selection = finder.find(spec1ItShouldIn1)
    expectSelection(spec1ItShouldIn1Selection, suiteClass.getName, "A Stack should pop values in last-in-first-out order", Array("A Stack should pop values in last-in-first-out order"))
    
    val spec1NestedSelection = finder.find(spec1Nested)
    expectSelection(spec1NestedSelection, suiteClass.getName, "A Stack should pop values in last-in-first-out order", Array("A Stack should pop values in last-in-first-out order"))
    
    val spec1ItShouldIn2Selection = finder.find(spec1ItShouldIn2)
    expectSelection(spec1ItShouldIn2Selection, suiteClass.getName, "A Stack should throw NoSuchElementException if an empty stack is popped", Array("A Stack should throw NoSuchElementException if an empty stack is popped"))
    
    val spec1BehaviorOf2Selection = finder.find(spec1BehaviorOf2)
    expectSelection(spec1BehaviorOf2Selection, suiteClass.getName, "A List", Array("A List should put values in the sequence they are put in", "A List should throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec1ItShouldIn3Selection = finder.find(spec1ItShouldIn3)
    expectSelection(spec1ItShouldIn3Selection, suiteClass.getName, "A List should put values in the sequence they are put in", Array("A List should put values in the sequence they are put in"))
    
    val spec1ItShouldIn4Selection = finder.find(spec1ItShouldIn4)
    expectSelection(spec1ItShouldIn4Selection, suiteClass.getName, "A List should throw ArrayIndexOutOfBoundsException when invalid index is applied", Array("A List should throw ArrayIndexOutOfBoundsException when invalid index is applied"))
  }
  
  test("FlatSpecFinder should find test name for tests written in test suite that extends org.scalatest.FlatSpec, using behavior of way and 'must'.") {
    class TestingFlatSpec1 extends AnyFlatSpec {
      behavior of "A Stack"

      it must "pop values in last-in-first-out order" in { 
        println("nested")
      }

      it must "throw NoSuchElementException if an empty stack is popped" in { 
        println("nested")
      }
      
      behavior of "A List"
      
      it must "put values in the sequence they are put in" in {
        
      }
      
      it must "throw ArrayIndexOutOfBoundsException when invalid index is applied" in {
        
      }
    }
    val suiteClass = classOf[TestingFlatSpec1]
    val spec1ClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFlatSpec1")
    val spec1Constructor = new ConstructorBlock(suiteClass.getName, spec1ClassDef, Array.empty)
    
    val spec1BehaviorOf1 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "behaviour"), spec1Constructor, Array.empty, "of", new StringLiteral(suiteClass.getName, null, "A Stack"))
    val spec1ItShould1 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "pop values in last-in-first-out order"))
    val spec1ItShouldIn1 = new MethodInvocation(suiteClass.getName, spec1ItShould1, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec1Nested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), spec1ItShouldIn1, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested"))
    val spec1ItShould2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "throw NoSuchElementException if an empty stack is popped"))
    val spec1ItShouldIn2 = new MethodInvocation(suiteClass.getName, spec1ItShould2, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val spec1BehaviorOf2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "behaviour"), spec1Constructor, Array.empty, "of", new StringLiteral(suiteClass.getName, null, "A List"))
    val spec1ItShould3 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "put values in the sequence they are put in"))
    val spec1ItShouldIn3 = new MethodInvocation(suiteClass.getName, spec1ItShould3, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec1ItShould4 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    val spec1ItShouldIn4 = new MethodInvocation(suiteClass.getName, spec1ItShould4, spec1Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    List[AstNode](spec1Constructor, spec1BehaviorOf1, spec1ItShould1, spec1ItShouldIn1, spec1ItShould2, spec1ItShouldIn2, 
                  spec1BehaviorOf2, spec1ItShould3, spec1ItShouldIn3, spec1ItShould4, spec1ItShouldIn4).foreach(_.parent)
    
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FlatSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FlatSpecFinder], "Suite that uses org.scalatest.FlatSpec should use FlatSpecFinder.")
    
    val spec1ConstructorSelection = finder.find(spec1Constructor)
    expectSelection(spec1ConstructorSelection, suiteClass.getName, suiteClass.getName, Array("A Stack must pop values in last-in-first-out order", "A Stack must throw NoSuchElementException if an empty stack is popped", "A List must put values in the sequence they are put in", "A List must throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec1BehaviorOf1Selection = finder.find(spec1BehaviorOf1)
    expectSelection(spec1BehaviorOf1Selection, suiteClass.getName, "A Stack", Array("A Stack must pop values in last-in-first-out order", "A Stack must throw NoSuchElementException if an empty stack is popped"))
    
    val spec1ItShouldIn1Selection = finder.find(spec1ItShouldIn1)
    expectSelection(spec1ItShouldIn1Selection, suiteClass.getName, "A Stack must pop values in last-in-first-out order", Array("A Stack must pop values in last-in-first-out order"))
    
    val spec1NestedSelection = finder.find(spec1Nested)
    expectSelection(spec1NestedSelection, suiteClass.getName, "A Stack must pop values in last-in-first-out order", Array("A Stack must pop values in last-in-first-out order"))
    
    val spec1ItShouldIn2Selection = finder.find(spec1ItShouldIn2)
    expectSelection(spec1ItShouldIn2Selection, suiteClass.getName, "A Stack must throw NoSuchElementException if an empty stack is popped", Array("A Stack must throw NoSuchElementException if an empty stack is popped"))
    
    val spec1BehaviorOf2Selection = finder.find(spec1BehaviorOf2)
    expectSelection(spec1BehaviorOf2Selection, suiteClass.getName, "A List", Array("A List must put values in the sequence they are put in", "A List must throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec1ItShouldIn3Selection = finder.find(spec1ItShouldIn3)
    expectSelection(spec1ItShouldIn3Selection, suiteClass.getName, "A List must put values in the sequence they are put in", Array("A List must put values in the sequence they are put in"))
    
    val spec1ItShouldIn4Selection = finder.find(spec1ItShouldIn4)
    expectSelection(spec1ItShouldIn4Selection, suiteClass.getName, "A List must throw ArrayIndexOutOfBoundsException when invalid index is applied", Array("A List must throw ArrayIndexOutOfBoundsException when invalid index is applied"))
  }
  
  test("FlatSpecFinder should find test name for tests written in test suite that extends org.scalatest.FlatSpec, using short-hand way and 'should'") {
    class TestingFlatSpec2 extends AnyFlatSpec {
      "A Stack" should "pop values in last-in-first-out order" in { 
        println("nested")
      }

      it should "throw NoSuchElementException if an empty stack is popped" in { 
        
      }
      
      "A List" should "put values in the sequence they are put in" in {
        
      }
      
      it should "throw ArrayIndexOutOfBoundsException when invalid index is applied" in {
        
      }
    }
    
    val suiteClass = classOf[TestingFlatSpec2]
    val spec2ClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFlatSpec2")
    val spec2Constructor = new ConstructorBlock(suiteClass.getName, spec2ClassDef, Array.empty)
    
    val spec2ItShould1 =new  MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "A Stack"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "pop values in last-in-first-out order"))
    val spec2ItShouldIn1 = new MethodInvocation(suiteClass.getName, spec2ItShould1, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec2Nested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), spec2ItShouldIn1, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested"))
    val spec2ItShould2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "throw NoSuchElementException if an empty stack is popped"))
    val spec2ItShouldIn2 = new MethodInvocation(suiteClass.getName, spec2ItShould2, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val spec2ItShould3 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "A List"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "put values in the sequence they are put in"))
    val spec2ItShouldIn3 = new MethodInvocation(suiteClass.getName, spec2ItShould3, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec2ItShould4 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "should", new StringLiteral(suiteClass.getName, null, "throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    val spec2ItShouldIn4 = new MethodInvocation(suiteClass.getName, spec2ItShould4, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    List[AstNode](spec2Constructor, spec2ItShould1, spec2ItShouldIn1, spec2ItShould2, spec2ItShouldIn2, 
                  spec2ItShould3, spec2ItShouldIn3, spec2ItShould4, spec2ItShouldIn4).foreach(_.parent)
                  
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FlatSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FlatSpecFinder], "Suite that uses org.scalatest.FlatSpec should use FlatSpecFinder.")
                  
    val spec2ConstructorSelection = finder.find(spec2Constructor)
    expectSelection(spec2ConstructorSelection, suiteClass.getName, suiteClass.getName, Array("A Stack should pop values in last-in-first-out order", "A Stack should throw NoSuchElementException if an empty stack is popped", "A List should put values in the sequence they are put in", "A List should throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec2ItShouldIn1Selection = finder.find(spec2ItShouldIn1)
    expectSelection(spec2ItShouldIn1Selection, suiteClass.getName, "A Stack should pop values in last-in-first-out order", Array("A Stack should pop values in last-in-first-out order"))
    
    val spec2NestedSelection = finder.find(spec2Nested)
    expectSelection(spec2NestedSelection, suiteClass.getName, "A Stack should pop values in last-in-first-out order", Array("A Stack should pop values in last-in-first-out order"))
    
    val spec2ItShouldIn2Selection = finder.find(spec2ItShouldIn2)
    expectSelection(spec2ItShouldIn2Selection, suiteClass.getName, "A Stack should throw NoSuchElementException if an empty stack is popped", Array("A Stack should throw NoSuchElementException if an empty stack is popped"))
    
    val spec2ItShouldIn3Selection = finder.find(spec2ItShouldIn3)
    expectSelection(spec2ItShouldIn3Selection, suiteClass.getName, "A List should put values in the sequence they are put in", Array("A List should put values in the sequence they are put in"))
    
    val spec2ItShouldIn4Selection = finder.find(spec2ItShouldIn4)
    expectSelection(spec2ItShouldIn4Selection, suiteClass.getName, "A List should throw ArrayIndexOutOfBoundsException when invalid index is applied", Array("A List should throw ArrayIndexOutOfBoundsException when invalid index is applied"))
  }
  
  test("FlatSpecFinder should find test name for tests written in test suite that extends org.scalatest.FlatSpec, using short-hand way and 'must'") {
    class TestingFlatSpec2 extends AnyFlatSpec {
      "A Stack" must "pop values in last-in-first-out order" in { 
        println("nested")
      }

      it must "throw NoSuchElementException if an empty stack is popped" in { 
        
      }
      
      "A List" must "put values in the sequence they are put in" in {
        
      }
      
      it must "throw ArrayIndexOutOfBoundsException when invalid index is applied" in {
        
      }
    }
    
    val suiteClass = classOf[TestingFlatSpec2]
    val spec2ClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFlatSpec2")
    val spec2Constructor = new ConstructorBlock(suiteClass.getName, spec2ClassDef, Array.empty)
    
    val spec2ItShould1 =new  MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "A Stack"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "pop values in last-in-first-out order"))
    val spec2ItShouldIn1 = new MethodInvocation(suiteClass.getName, spec2ItShould1, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec2Nested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), spec2ItShouldIn1, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested"))
    val spec2ItShould2 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "throw NoSuchElementException if an empty stack is popped"))
    val spec2ItShouldIn2 = new MethodInvocation(suiteClass.getName, spec2ItShould2, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val spec2ItShould3 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "A List"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "put values in the sequence they are put in"))
    val spec2ItShouldIn3 = new MethodInvocation(suiteClass.getName, spec2ItShould3, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val spec2ItShould4 = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "it"), null, Array.empty, "must", new StringLiteral(suiteClass.getName, null, "throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    val spec2ItShouldIn4 = new MethodInvocation(suiteClass.getName, spec2ItShould4, spec2Constructor, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    List[AstNode](spec2Constructor, spec2ItShould1, spec2ItShouldIn1, spec2ItShould2, spec2ItShouldIn2, 
                  spec2ItShould3, spec2ItShouldIn3, spec2ItShould4, spec2ItShouldIn4).foreach(_.parent)
                  
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FlatSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FlatSpecFinder], "Suite that uses org.scalatest.FlatSpec should use FlatSpecFinder.")
                  
    val spec2ConstructorSelection = finder.find(spec2Constructor)
    expectSelection(spec2ConstructorSelection, suiteClass.getName, suiteClass.getName, Array("A Stack must pop values in last-in-first-out order", "A Stack must throw NoSuchElementException if an empty stack is popped", "A List must put values in the sequence they are put in", "A List must throw ArrayIndexOutOfBoundsException when invalid index is applied"))
    
    val spec2ItShouldIn1Selection = finder.find(spec2ItShouldIn1)
    expectSelection(spec2ItShouldIn1Selection, suiteClass.getName, "A Stack must pop values in last-in-first-out order", Array("A Stack must pop values in last-in-first-out order"))
    
    val spec2NestedSelection = finder.find(spec2Nested)
    expectSelection(spec2NestedSelection, suiteClass.getName, "A Stack must pop values in last-in-first-out order", Array("A Stack must pop values in last-in-first-out order"))
    
    val spec2ItShouldIn2Selection = finder.find(spec2ItShouldIn2)
    expectSelection(spec2ItShouldIn2Selection, suiteClass.getName, "A Stack must throw NoSuchElementException if an empty stack is popped", Array("A Stack must throw NoSuchElementException if an empty stack is popped"))
    
    val spec2ItShouldIn3Selection = finder.find(spec2ItShouldIn3)
    expectSelection(spec2ItShouldIn3Selection, suiteClass.getName, "A List must put values in the sequence they are put in", Array("A List must put values in the sequence they are put in"))
    
    val spec2ItShouldIn4Selection = finder.find(spec2ItShouldIn4)
    expectSelection(spec2ItShouldIn4Selection, suiteClass.getName, "A List must throw ArrayIndexOutOfBoundsException when invalid index is applied", Array("A List must throw ArrayIndexOutOfBoundsException when invalid index is applied"))
  }
  
}