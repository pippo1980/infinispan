/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.server.core

import org.infinispan.util.logging.{LogFactory, Log}

/**
 * A logging facade for scala code.
 *
 * @author Galder Zamarreño
 * @since 4.1
 */
trait Logging {
   private lazy val log: Log = LogFactory.getLog(getClass)

   // params.map(_.asInstanceOf[AnyRef]) => returns a Seq[AnyRef]
   // the ': _*' part tells the compiler to pass it as varargs
   def info(msg: => String, params: Any*) = log.info(msg, params.map(_.asInstanceOf[AnyRef]) : _*)

   def isDebugEnabled = log.isDebugEnabled

   def debug(msg: => String, params: Any*) = log.debug(msg, params.map(_.asInstanceOf[AnyRef]) : _*)

   def isTraceEnabled = log.isTraceEnabled

   def trace(msg: => String, params: Any*) = log.trace(msg, params.map(_.asInstanceOf[AnyRef]) : _*)

   def warn(msg: => String, params: Any*) = log.warn(msg, params.map(_.asInstanceOf[AnyRef]) : _*)

   def warn(msg: => String, t: Throwable) = log.warn(msg, t, null)

   def error(msg: => String) = log.error(msg, null)

   def error(msg: => String, t: Throwable) = log.error(msg, t, null)

}