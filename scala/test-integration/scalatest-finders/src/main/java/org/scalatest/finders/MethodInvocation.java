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

public class MethodInvocation implements AstNode {
  private final String className;
  private final AstNode target;
  private AstNode parent; // TODO: due to cyclic references, e.g. target can be a child as well, try to rewrite with proper constructors
  private final List<AstNode> children;
  private final String name;
  private final AstNode[] args;
  
  public MethodInvocation(String className, AstNode target, AstNode parent, AstNode[] children, String name, AstNode... args) {
    this.className = className;
    this.target = target;
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
    this.children = new ArrayList<>();
    this.children.addAll(Arrays.asList(children));
    this.name = name;
    this.args = args;
  }
  
  @Override
  public String className() {
    return className;
  }
  
  @Override
  public AstNode parent() {
    return parent;
  }

  // TestsOnly, in cases of cyclic dependencies
  public void injectParent(AstNode newParent) {
    if (parent != null) throw new AssertionError("parent already exists");
    parent = newParent;
    parent.addChild(this);
  }

  @Override
  public AstNode[] children() {
    return children.toArray(new AstNode[0]);
  }
  
  @Override
  public String name() {
    return name;
  }
  
  @Override
  public void addChild(AstNode node) {
    if (!children.contains(node))
      children.add(node);
  }

  @Override
  public boolean canBePartOfTestName() {
    for (AstNode child: children) {
      if (!child.canBePartOfTestName()) {
        return false;
      }
    }
    return true;
  }

  public AstNode target() {
    return target;
  }
  
  public AstNode[] args() {
    return args;  
  }
}