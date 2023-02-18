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

package org.scalatest.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.scalatest.finders.utils.StringUtils.is;

public class FreeSpecFinder implements Finder {
  
  private String getTestNameBottomUp(MethodInvocation invocation) {
    StringBuilder result = new StringBuilder();
    while (invocation != null) {
      if (invocation.target().name().equals("taggedAs") && invocation.target() instanceof MethodInvocation taggedInvocation) {
        if (!taggedInvocation.target().canBePartOfTestName()) return null;
        result.insert(0, taggedInvocation.target().toString() + " ");
      } else {
        if (!invocation.target().canBePartOfTestName()) return null;
        result.insert(0, invocation.target().toString() + " ");
      }
      if (invocation.parent() != null && invocation.parent() instanceof MethodInvocation)
        invocation = (MethodInvocation) invocation.parent();
      else
        invocation = null;
    }
    return result.toString().trim();
  }
   
  private List<String> getTestNamesTopDown(MethodInvocation invocation) {
    List<String> results = new ArrayList<>();
    List<AstNode> nodes = new ArrayList<>();
    nodes.add(invocation);
      
    while (nodes.size() > 0) {
      AstNode head = nodes.remove(0);
      if (head instanceof MethodInvocation headInvocation) {
        if (is(headInvocation.name(), "in", "is")) {
          String testName = getTestNameBottomUp(headInvocation);
          if (testName != null) {
            results.add(testName);
          }
        }
        else
          nodes.addAll(0, Arrays.asList(headInvocation.children()));
      }
    }
      
    return results;
  }
  
  @Override
  public Selection find(AstNode node) {
    Selection result = null;
    while (result == null) {
      if (node instanceof MethodInvocation invocation) {
        String name = invocation.name();
        AstNode parent = invocation.parent();

        if (is(name, "in") && parent instanceof ConstructorBlock) {
          String testName = getTestNameBottomUp(invocation);
          result = testName == null ? null : new Selection(invocation.className(), testName, new String[] {testName});
        }
        else if (is(name, "in", "is") && parent.name().equals("-")) {
          String testName = getTestNameBottomUp(invocation);
          result = testName == null ? null : new Selection(invocation.className(), testName, new String[]{testName});
        }
        else if (name.equals("-")) {
          String displayName = getTestNameBottomUp(invocation);
          List<String> testNames = getTestNamesTopDown(invocation);
          result = new Selection(invocation.className(), displayName, testNames.toArray(new String[0]));
        }
      }
        
      if (result == null) {
        AstNode parent = node.parent();
        if (parent != null)
          node = parent;
        else
          break;
      }
    }
    return result;
  }
}
