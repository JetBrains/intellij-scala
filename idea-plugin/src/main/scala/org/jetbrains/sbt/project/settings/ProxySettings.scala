package org.jetbrains.sbt
package project.settings

/**
 * @author Pavel Fatin
 */
case class ProxySettings(proxyRequired: Boolean,
                         host: String,
                         port: Int,
                         authenticationRequired: Boolean,
                         login: String,
                         password: String)