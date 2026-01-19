package org.jetbrains.plugins.scala.testingSupport.test.munit

/**
 * A marker trait for distinguishing the `MUnitTestFramework`. The testing support for the MUnit test framework was
 * moved into a separate module because it has a hard dependency on the JUnit plugin for IntelliJ IDEA. We cannot link
 * against the class directly as that would be a circular dependency.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-24859 SCL-24859]]
 */
private[test] trait MUnitTestFrameworkMarker
