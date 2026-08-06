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

public class ToStringTarget implements AstNode {
    
  private final String className;
  private AstNode parent;
  private final List<AstNode> children;
  private final Object target;
    
  public ToStringTarget(String className, AstNode parent, AstNode[] children, Object target) {
    this.className = className;
    this.parent = parent;
    if (parent != null)
        parent.addChild(this);
    this.children = new ArrayList<>();
    this.children.addAll(Arrays.asList(children));
    this.target = target;
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
    return new AstNode[0];
  }
  
  @Override
  public String name() {
    return target.toString();
  }
    
  @Override
  public void addChild(AstNode node) {
    if (!children.contains(node))
      children.add(node);
  }

  @Override
  public boolean canBePartOfTestName() {
    return true;
  }

  @Override
  public String toString() {
    return target.toString();
  }
}