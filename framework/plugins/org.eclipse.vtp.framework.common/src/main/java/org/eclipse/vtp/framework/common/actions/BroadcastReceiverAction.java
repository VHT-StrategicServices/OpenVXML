/*--------------------------------------------------------------------------
 * Copyright (c) 2004, 2006-2007 OpenMethods, LLC
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Trip Gilman (OpenMethods), Lonnie G. Pryor (OpenMethods), 
 *    Randy Childers (OpenMethods)
 *    - initial API and implementation
 -------------------------------------------------------------------------*/
package org.eclipse.vtp.framework.common.actions;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.vtp.framework.core.IAction;
import org.eclipse.vtp.framework.core.IActionContext;
import org.eclipse.vtp.framework.core.IActionResult;
import org.eclipse.vtp.framework.core.IReporter;

/**
 * An action that does nothing.
 * 
 * @author Lonnie Pryor
 */
public class BroadcastReceiverAction implements IAction
{
	/** The context to use. */
	private final IActionContext context;

	/**
	 * Creates a new BranchAction.
	 * 
	 * @param context The context to use.
	 */
	public BroadcastReceiverAction(IActionContext context)
	{
		this.context = context;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.core.IAction#execute()
	 */
	public IActionResult execute()
	{
		if(context.isReportingEnabled())
		{
			Dictionary<String, Object> props = new Hashtable<String, Object>();
			props.put("event", "broadcast receiver");
			context.report(IReporter.SEVERITY_INFO, "Exit caught by receiver.", props);
		}
		return context.createResult(IActionResult.RESULT_NAME_DEFAULT);
	}
}
