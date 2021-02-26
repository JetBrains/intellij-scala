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

import org.scalatest.Spec

class SpecFinderSuite extends FinderSuite {

  test("SpecFinder should find test name for tests written in test suite that extends org.scalatest.Spec") {
    class TestingSpec extends Spec {
      object `A Stack` {
        object `whenever it is empty` {
          println("nested -")
          object `certainly ought to` {
            def `be empty` {
        
            }
            def `complain on peek` {
              println("in nested")
            }
            def `complain on pop` {
          
            }
          }
        }
        object `but when full, by contrast, must` {
          def `be full` {
            
          }
          def `complain on push` {
          
          }
        }
      }
    }
    
    val suiteClass = classOf[TestingSpec]
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.Spec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[SpecFinder], "Suite that uses org.scalatest.Spec should use SpecFinder.")
    
    val classDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingSpec")
    val classDefConstructor = new ConstructorBlock(suiteClass.getName, classDef, Array.empty)
    val aStackNodeDef = new ModuleDefinition(suiteClass.getName, classDefConstructor, Array.empty, "A Stack")
    val aStackNodeConstructor = new ConstructorBlock(suiteClass.getName, aStackNodeDef, Array.empty)
    val wheneverItIsEmptyDef = new ModuleDefinition(suiteClass.getName, aStackNodeConstructor, Array.empty, "whenever it is empty")
    val wheneverItIsEmptyConstructor = new ConstructorBlock(suiteClass.getName, wheneverItIsEmptyDef, Array.empty)
    val nestedDash = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), wheneverItIsEmptyConstructor, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested in"))
    val certainlyOughtToDef = new ModuleDefinition(suiteClass.getName, wheneverItIsEmptyConstructor, Array.empty, "certainly ought to")
    val certainlyOughtToConstructor = new ConstructorBlock(suiteClass.getName, certainlyOughtToDef, Array.empty)
    val beEmpty = new MethodDefinition(suiteClass.getName, certainlyOughtToConstructor, Array.empty, "be empty")
    val complainOnPeek = new MethodDefinition(suiteClass.getName, certainlyOughtToConstructor, Array.empty, "complain on peek") 
    val inNested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), complainOnPeek, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "in nested"))
    val complainOnPop = new MethodDefinition(suiteClass.getName, certainlyOughtToConstructor, Array.empty, "complain on pop")
    val butWhenFullByContrastMustDef = new ModuleDefinition(suiteClass.getName, aStackNodeConstructor, Array.empty, "but when full, by contrast, must")
    val butWhenFullByContrastMustConstructor = new ConstructorBlock(suiteClass.getName, butWhenFullByContrastMustDef, Array.empty)
    val beFull = new MethodDefinition(suiteClass.getName, butWhenFullByContrastMustConstructor, Array.empty, "be full")
    val complainOnPush = new MethodDefinition(suiteClass.getName, butWhenFullByContrastMustConstructor, Array.empty, "complain on push")
    
    List[AstNode](classDef, classDefConstructor, aStackNodeDef, aStackNodeConstructor, wheneverItIsEmptyDef, wheneverItIsEmptyConstructor, certainlyOughtToDef, certainlyOughtToConstructor, beEmpty, complainOnPeek, complainOnPop, 
                  butWhenFullByContrastMustDef, butWhenFullByContrastMustConstructor, beFull, complainOnPush).foreach(_.parent)
        
    val aStackTest = finder.find(aStackNodeDef)
    expectSelection(aStackTest, suiteClass.getName, "A Stack", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop", 
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"
    ))
    
    val wheneverItIsEmptyTest = finder.find(wheneverItIsEmptyDef)
    expectSelection(wheneverItIsEmptyTest, suiteClass.getName, "A Stack whenever it is empty", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val nestedDashTest = finder.find(nestedDash)
    expectSelection(nestedDashTest, suiteClass.getName, "A Stack whenever it is empty", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val certainlyOughtToTest = finder.find(certainlyOughtToDef)
    expectSelection(certainlyOughtToTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val beEmptyTest = finder.find(beEmpty)
    expectSelection(beEmptyTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to be empty", Array("A Stack whenever it is empty certainly ought to be empty"))
    
    val complainOnPeekTest = finder.find(complainOnPeek)
    expectSelection(complainOnPeekTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on peek", Array("A Stack whenever it is empty certainly ought to complain on peek"))
    
    val inNestedTest = finder.find(inNested)
    expectSelection(inNestedTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on peek", Array("A Stack whenever it is empty certainly ought to complain on peek"))
    
    val complainOnPopTest = finder.find(complainOnPop)
    expectSelection(complainOnPopTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on pop", Array("A Stack whenever it is empty certainly ought to complain on pop"))
    
    val butWhenFullByContrastMustTest = finder.find(butWhenFullByContrastMustDef)
    expectSelection(butWhenFullByContrastMustTest, suiteClass.getName, "A Stack but when full, by contrast, must", Array(
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"    
    ))
    
    val beFullTest = finder.find(beFull)
    expectSelection(beFullTest, suiteClass.getName, "A Stack but when full, by contrast, must be full", Array("A Stack but when full, by contrast, must be full"))
    
    val complainOnPushTest = finder.find(complainOnPush)
    expectSelection(complainOnPushTest, suiteClass.getName, "A Stack but when full, by contrast, must complain on push", Array("A Stack but when full, by contrast, must complain on push"))
  }
  
}