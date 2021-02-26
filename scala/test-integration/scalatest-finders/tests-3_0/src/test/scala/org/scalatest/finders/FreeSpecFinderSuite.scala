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

import org.scalatest.FreeSpec

class FreeSpecFinderSuite extends FinderSuite {
  
  test("FreeSpecFinder should find test name for tests written in test suite that extends org.scalatest.FreeSpec") {
    class TestingFreeSpec extends FreeSpec {
      "A Stack" - {
        "whenever it is empty" - {
          println("nested -")
          "certainly ought to" - {
            "be empty" in {
        
            }
            "complain on peek" in {
              println("in nested")
            }
            "complain on pop" in {
          
            }
          }
        }
        "but when full, by contrast, must" - {
          "be full" in {
            "in nested" in {
              
            }
          }
          "complain on push" in {
          
          }
        }
      }
    }
    val suiteClass = classOf[TestingFreeSpec]
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FreeSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FreeSpecFinder], "Suite that uses org.scalatest.FreeSpec should use FreeSpecFinder.")
    
    val aStackNode: MethodInvocation = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "A Stack"), null, Array.empty, "-", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val wheneverItIsEmpty = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "whenever it is empty"), aStackNode, Array.empty, "-", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}")) 
    val nestedDash = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), wheneverItIsEmpty, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested in"))
    val certainlyOughtTo = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "certainly ought to"), wheneverItIsEmpty, Array.empty, "-", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val beEmpty = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "be empty"), certainlyOughtTo, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val complainOnPeek = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "complain on peek"), certainlyOughtTo, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}")) 
    val inNested = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "{Predef}"), complainOnPeek, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "in nested"))
    val complainOnPop = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "complain on pop"), certainlyOughtTo, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val butWhenFullByContrastMust = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "but when full, by contrast, must"), aStackNode, Array.empty, "-", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val beFull = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "be full"), butWhenFullByContrastMust, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val nestedIn = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "in nested"), beFull, Array.empty, "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val complainOnPush = new MethodInvocation(suiteClass.getName, new ToStringTarget(suiteClass.getName, null, Array.empty, "complain on push"), butWhenFullByContrastMust, Array(), "in", new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    List[AstNode](aStackNode, wheneverItIsEmpty, certainlyOughtTo, beEmpty, complainOnPeek, complainOnPop, butWhenFullByContrastMust, 
        beFull, complainOnPush).foreach(_.parent)
    
    val aStackTest = finder.find(aStackNode)
    expectSelection(aStackTest, suiteClass.getName, "A Stack", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop", 
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"
    ))
    
    val wheneverItIsEmptyTest = finder.find(wheneverItIsEmpty)
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
    
    val certainlyOughtToTest = finder.find(certainlyOughtTo)
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
    
    val butWhenFullByContrastMustTest = finder.find(butWhenFullByContrastMust)
    expectSelection(butWhenFullByContrastMustTest, suiteClass.getName, "A Stack but when full, by contrast, must", Array(
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"    
    ))
    
    val beFullTest = finder.find(beFull)
    expectSelection(beFullTest, suiteClass.getName, "A Stack but when full, by contrast, must be full", Array("A Stack but when full, by contrast, must be full"))
    
    val nestedInTest = finder.find(nestedIn)
    expectSelection(nestedInTest, suiteClass.getName, "A Stack but when full, by contrast, must be full", Array("A Stack but when full, by contrast, must be full"))
    
    val complainOnPushTest = finder.find(complainOnPush)
    expectSelection(complainOnPushTest, suiteClass.getName, "A Stack but when full, by contrast, must complain on push", Array("A Stack but when full, by contrast, must complain on push"))
  }
  
}