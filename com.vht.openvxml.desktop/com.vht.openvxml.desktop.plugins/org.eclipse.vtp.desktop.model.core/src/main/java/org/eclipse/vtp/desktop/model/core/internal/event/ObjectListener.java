/*--------------------------------------------------------------------------
 * Copyright (c) 2004, 2006-2007 OpenMethods, LLC
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Trip Gilman (OpenMethods), Lonnie G. Pryor (OpenMethods)
 *    - initial API and implementation
 -------------------------------------------------------------------------*/
package org.eclipse.vtp.desktop.model.core.internal.event;

/**
 * This interface represents a listener of OpenVXML project object model events.
 *
 * @author Trip Gilman
 * @version 2.0
 */
public interface ObjectListener {
	/**
	 * Called when an event is generated by the object being listened to.
	 *
	 * @param event
	 *            The event that occured
	 */
	public void processObjectEvent(ObjectEvent event);
}