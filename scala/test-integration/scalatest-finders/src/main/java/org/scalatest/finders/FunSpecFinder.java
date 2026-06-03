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

public class FunSpecFinder implements Finder {
  
  private String getTestNameBottomUp(MethodInvocation invocation) {
    if (!invocation.args()[0].canBePartOfTestName()) return null;
    String result = invocation.args()[0].toString();
    AstNode node = invocation.parent();
    while (node != null) {
      if (node instanceof MethodInvocation parentInvocation) {
        if (parentInvocation.name().equals("describe")) {
          if (parentInvocation.args()[0].canBePartOfTestName()) {
            result = parentInvocation.args()[0].toString() + " " + result;
          } else {
            return null;
          }
        }
      }
      
      if (node.parent() != null && node.parent() instanceof MethodInvocation)
        node = node.parent();
      else
        node = null;
    }
    return result.trim();
  }
  
  private List<String> getTestNamesTopDown(MethodInvocation invocation) {
    List<String> results = new ArrayList<>();
    List<AstNode> nodes = new ArrayList<>();
    nodes.add(invocation);
    
    while (nodes.size() > 0) {
      AstNode head = nodes.remove(0);
      if (head instanceof MethodInvocation headInvocation) {
        if (headInvocation.name().equals("apply") && headInvocation.target() instanceof ToStringTarget && headInvocation.target().toString().equals("it")) {
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
        if (name.equals("apply")) {
          String testName = getTestNameBottomUp(invocation);
          result = testName != null ? new Selection(invocation.className(), testName, new String[] { testName }) : null;
          if (testName == null) {
            if (node.parent() != null) {
              node = node.parent();
            } else break;
          }
        }
        else if (name.equals("describe")) {
          String displayName = getTestNameBottomUp(invocation);
          List<String> testNames = getTestNamesTopDown(invocation);
          result = new Selection(invocation.className(), displayName, testNames.toArray(new String[0]));
        }
        else {
          if (node.parent() != null) 
            node = node.parent();
          else
            break;
        }
      }
      else {
        if (node.parent() != null) 
          node = node.parent();
        else
          break;
      }
    }
    return result;
  }
}