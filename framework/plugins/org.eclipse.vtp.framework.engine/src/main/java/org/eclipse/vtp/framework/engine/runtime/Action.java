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
package org.eclipse.vtp.framework.engine.runtime;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.io.DataOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.eclipse.vtp.framework.core.IAction;
import org.eclipse.vtp.framework.core.IActionContext;
import org.eclipse.vtp.framework.core.IActionResult;
import org.eclipse.vtp.framework.core.IReporter;
import org.eclipse.vtp.framework.engine.ActionDescriptor;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.w3c.dom.Element;

/**
 * Represents an action that can be run by the engine.
 * 
 * @author Lonnie Pryor
 */
public class Action extends Executable
{
	/** The name of the action. */
	private final String name;
	/** The descriptor of the action. */
	private final ActionDescriptor descriptor;
	/** The index of results by ID. */
	private final Map<String, Executable> resultPaths = new HashMap<String, Executable>();

	private static HttpClient httpClient = null;
	
	static
    {
        org.apache.http.params.HttpParams params = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(params, 600);
        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(300);
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        org.apache.http.conn.ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        httpClient = new DefaultHttpClient(cm, params);
    }
	
	/**
	 * Creates a new Action.
	 * 
	 * @param blueprint The blueprint of the process.
	 * @param name The name of the action.
	 * @param elements The configuration data or <code>null</code> for no
	 *          configuration data.
	 * @param instanceID The ID of this instance of the action.
	 * @param descriptor The descriptor of the action.
	 * @throws NullPointerException If the supplied blueprint is <code>null</code>.
	 * @throws NullPointerException If the supplied instance ID is
	 *           <code>null</code>.
	 * @throws NullPointerException If the supplied descriptor is
	 *           <code>null</code>.
	 */
	public Action(Blueprint blueprint, String name, Element[] elements, String instanceID,
			ActionDescriptor descriptor) throws NullPointerException
	{
		super(blueprint, descriptor == null ? null : descriptor.getType(), elements, instanceID);
		if (descriptor == null)
			throw new NullPointerException("descriptor"); //$NON-NLS-1$
		this.name = name;
		this.descriptor = descriptor;
		this.resultPaths.put(IActionResult.RESULT_NAME_REPEAT, this);
	}

	/**
	 * Configures an available result of this action.
	 * 
	 * @param resultID The ID of the result path to configure.
	 * @param next The next executable in the path.
	 */
	public void configure(String resultID, Executable next)
	{
		resultPaths.put(resultID, next);
	}
	
	/**
	 * Returns the name of the action.
	 *
	 * @return The name of the action.
	 */
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.engine.runtime.Executable#
	 *      getActionInstance()
	 */
	public Action getActionInstance()
	{
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.engine.runtime.Executable#getActionState()
	 */
	public int getActionState()
	{
		return IActionContext.STATE_DURING;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.engine.runtime.Executable#isBlocking()
	 */
	public boolean isBlocking()
	{
		return descriptor.isBlocking();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.engine.runtime.Executable#execute(
	 *      org.eclipse.vtp.framework.engine.runtime.Sequence)
	 */
	public Executable execute(Sequence sequence)
	{
		Dictionary report = new Hashtable();
		report.put("event", "action.starting");
		sequence.context.report(IReporter.SEVERITY_INFO, "Action \""
				+ getName() + "\" Starting", report);
		IActionResult actionResult = null;
		try
		{
			actionResult = ((IAction)createInstance(sequence)).execute();
			sequence.context.clearParameters();
		}
		catch (RuntimeException e)
		{
			e.printStackTrace();
			actionResult = sequence.context.createResult(null, e);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			actionResult = sequence.context.createResult(null, t);
		}
		report = new Hashtable();
		report.put("event", "action.ended");
		sequence.context.report(IReporter.SEVERITY_INFO, "Action \""
				+ getName() + "\" Ended", report);
		try
		{
			sendNavigatorMessage(sequence);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			actionResult = sequence.context.createResult(null, e);
		}
		if (actionResult == null)
			actionResult = sequence.context.createResult(null);
		Throwable cause = actionResult.getFailureCause();
		if (cause != null && sequence.context.isErrorEnabled())
		{
			Hashtable properties = new Hashtable();
			properties.put("cause", cause); //$NON-NLS-1$
			sequence.context.error(cause.getMessage(), properties);
		}
		String resultName = actionResult.getName();
		sequence.context.info("Leaving Action \"" + getName() + "\" through \"" + resultName + "\".");
		Executable next = resultPaths.get(resultName);
		while (next == null)
		{
			if (IActionResult.RESULT_NAME_ABORT.equals(resultName))
				return null;
			else if (IActionResult.RESULT_NAME_ERROR.equals(resultName))
				resultName = IActionResult.RESULT_NAME_ABORT;
			else if (IActionResult.RESULT_NAME_DEFAULT.equals(resultName))
				resultName = IActionResult.RESULT_NAME_ERROR;
			else
			{
				int lastDot = resultName.lastIndexOf('.');
				if (lastDot < 0)
					resultName = IActionResult.RESULT_NAME_DEFAULT;
				else
					resultName = resultName.substring(0, lastDot);
			}
			next = resultPaths.get(resultName);
		}
		return next;
	}
	
	public void sendNavigatorMessage(Sequence sequence) throws Exception
	{
		try
		{
			String sessionId = sequence.context.getSessionID();
			String moduleName = getName();
			URI uri = new URI((String)sequence.context.getRootAttribute("com.virtualhold.toolkit.navigatorUrl"));
			HttpContext httpContext = new BasicHttpContext();
			CookieStore cookieStore = new BasicCookieStore();
			httpContext.setAttribute("http.cookie-store", cookieStore);
			final HttpPost post = new HttpPost(uri);
			String jsonPayload = "{\"channel\": {\"type\": \"VIS\"},\"events\": [{\"type\": \"topic\",\"event_name\": \"Module_Change\"}],\"customer\": {\"session_id\": \"" + sessionId + "\" ,\"module_name\": \"" + moduleName + "\", \"email\": \"aacevedo@virtualhold.com\", \"phone1\": \"3306702205\", \"phone2\": \"2205\", \"Route_Point\":\"VH_Test\"},\"resources\": [{\"type\": \"None\"}]}";
			post.setEntity(new StringEntity(jsonPayload));
			post.setHeader("Content-Type", "application/json");
			httpClient.execute(post, httpContext);
		}
		catch(Exception e)
		{
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.engine.runtime.Configurable#
	 *      getComponentType()
	 */
	protected Class getComponentType()
	{
		return descriptor.getType();
	}
}
