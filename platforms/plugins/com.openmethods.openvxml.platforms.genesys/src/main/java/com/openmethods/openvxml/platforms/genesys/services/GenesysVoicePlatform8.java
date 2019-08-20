/**
 * 
 */
package com.openmethods.openvxml.platforms.genesys.services;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.vtp.framework.core.IActionContext;
import org.eclipse.vtp.framework.core.IExecutionContext;
import org.eclipse.vtp.framework.interactions.core.commands.InitialCommand;
import org.eclipse.vtp.framework.interactions.core.commands.MetaDataMessageCommand;
import org.eclipse.vtp.framework.interactions.core.commands.MetaDataRequestCommand;
import org.eclipse.vtp.framework.interactions.core.configurations.MetaDataRequestConfiguration;
import org.eclipse.vtp.framework.interactions.core.platforms.IDocument;
import org.eclipse.vtp.framework.interactions.core.platforms.ILink;
import org.eclipse.vtp.framework.interactions.core.platforms.ILinkFactory;
import org.eclipse.vtp.framework.interactions.core.services.ExtendedActionEventManager;
import org.eclipse.vtp.framework.interactions.voice.services.VoicePlatform;
import org.eclipse.vtp.framework.interactions.voice.vxml.Assignment;
import org.eclipse.vtp.framework.interactions.voice.vxml.Block;
import org.eclipse.vtp.framework.interactions.voice.vxml.Catch;
import org.eclipse.vtp.framework.interactions.voice.vxml.Dialog;
import org.eclipse.vtp.framework.interactions.voice.vxml.Form;
import org.eclipse.vtp.framework.interactions.voice.vxml.Goto;
import org.eclipse.vtp.framework.interactions.voice.vxml.If;
import org.eclipse.vtp.framework.interactions.voice.vxml.Script;
import org.eclipse.vtp.framework.interactions.voice.vxml.Submit;
import org.eclipse.vtp.framework.interactions.voice.vxml.VXMLDocument;
import org.eclipse.vtp.framework.interactions.voice.vxml.Variable;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmethods.openvxml.platforms.genesys.Activator;
import com.openmethods.openvxml.platforms.genesys.vxml.Receive;
import com.openmethods.openvxml.platforms.genesys.vxml.Send;

/**
 * @author trip
 *
 */
public class GenesysVoicePlatform8 extends VoicePlatform
{

	private boolean isCtiC = false;
	private IExecutionContext context;
	private ObjectMapper mapper = new ObjectMapper();
	
	
	/**
	 * 
	 */
	public GenesysVoicePlatform8(IExecutionContext context)
	{
		super(context);
		this.context = context;
		if(context.getRootAttribute("isCtiC") != null)
			isCtiC = Boolean.parseBoolean((String)context.getRootAttribute("isCtiC"));
	}


	@Override
    protected VXMLDocument createVXMLDocument(ILinkFactory links, Dialog dialog)
    {
		VXMLDocument document = super.createVXMLDocument(links, dialog);
		document.setProperty("documentmaxage", "0"); //$NON-NLS-1$ //$NON-NLS-2$
		document.setProperty("documentmaxstale", "0"); //$NON-NLS-1$ //$NON-NLS-2$
		document.setProperty("com.telera.speechenabled", Boolean.FALSE.toString()); 
		document.setProperty("fetchaudio", "");
		if(isCtiC)
		{
			document.setProperty("com.genesyslab.externalevents.enable", "false");
			document.setProperty("com.genesyslab.externalevents.queue", "true");
		}
		else
		{
			document.setProperty("com.genesyslab.externalevents.enable", "true");
    	}
		document.addOtherNamespace("gvp", "http://www.genesyslab.com/2006/vxml21-extension");
		return document;
    }
	
	/* (non-Javadoc)
	 * @see org.eclipse.vtp.framework.interactions.voice.services.VoicePlatform#generateInitialVariableRequests(java.util.Map)
	 */
	public void generateInitialVariableRequests(Map variables)
	{
		super.generateInitialVariableRequests(variables);
		variables.put("gvpUUID", "session.connection.uuid");
	}

	@Override
	public List<String> getPlatformVariableNames() {
		List<String> names = super.getPlatformVariableNames();
		names.add("gvpUserData");
		names.add("gvpUUID");
		names.add("gvpCtiC");
		return names;
	}

	@Override
	public String postProcessInitialVariable(String name, String originalValue)
	{		
		if("gvpUserData".equals(name) && originalValue != null) //TODO change this to use the gvpCtiC variable
		{
			System.out.println("gvpUserData: " + originalValue); //TODO cleanup
			if(originalValue.contains("gvp.rm.cti-call=1"))
			{
				System.out.println("Using cti-c"); //TODO cleanup
				context.setRootAttribute("isCtiC", "true");
				isCtiC = true;
			}
		}
		else if ("gvpCtiC".equals(name))
		{
			if(originalValue != null && originalValue.contains("gvp.rm.cti-call=1"))
			{
				context.setRootAttribute("isCtiC", "true");
				return "true";
			}
			else
			{
				return "false";
			}
		}
		return super.postProcessInitialVariable(name, originalValue);
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.vtp.framework.interactions.core.support.AbstractPlatform#
	 *      renderInitialDocument(
	 *      org.eclipse.vtp.framework.interactions.core.platforms.ILinkFactory,
	 *      org.eclipse.vtp.framework.interactions.core.commands.InitialCommand)
	 */
	protected IDocument renderInitialDocument(ILinkFactory links,
			InitialCommand initialCommand)
	{
		VXMLDocument document = new VXMLDocument();
		document.setProperty("documentmaxage", "0"); //$NON-NLS-1$ //$NON-NLS-2$
		document.setProperty("documentmaxstale", "0"); //$NON-NLS-1$ //$NON-NLS-2$
		document.setProperty("com.genesyslab.externalevents.enable", "true");
		Script jsonInclude = new Script();
		jsonInclude.setSrc(links.createIncludeLink(Activator.getDefault().getBundle().getSymbolicName() + "/includes/json.js").toString());
		document.addScript(jsonInclude);
		Form form = new Form("InitialForm"); //$NON-NLS-1$
		Map<String, String> varMap = new LinkedHashMap<String, String>();
		generateInitialVariableRequests(varMap);
		for (String key : varMap.keySet())
		{
			form.addVariable(new Variable(key, "''")); //$NON-NLS-1$
		}
		form.addVariable(new Variable("gvpUserData", "JSON.stringify(session.com.genesyslab.userdata)"));
		form.addVariable(new Variable("gvpCtiC", "JSON.stringify(session.com.genesyslab.userdata)"));
		String[] variables = initialCommand.getVariableNames();
		for (int i = 0; i < variables.length; ++i)
		{
			String value = initialCommand.getVariableValue(variables[i]);
			if (value == null)
				value = ""; //$NON-NLS-1$
			form.addVariable(new Variable(variables[i], "'" + value + "'"));
		}
		Block block = new Block("InitialBlock"); //$NON-NLS-1$
		for (String key : varMap.keySet())
		{
			block.addAction(new Assignment(key, varMap.get(key)));
		}
//		Script userDataScript = new Script();
//		userDataScript.appendText("for(var key in session.com.genesyslab.userdata)\r\n");
//		userDataScript.appendText("{\r\n");
//		userDataScript.appendText("\tif(gvpUserData != '')\r\n");
//		userDataScript.appendText("\t\tgvpUserData = gvpUserData + '&';\r\n");
//		userDataScript.appendText("\tgvpUserData = gvpUserData + key + '=' + session.com.genesyslab.userdata[key];\r\n");
//		userDataScript.appendText("}\r\n");
//		block.addAction(userDataScript);
		ILink nextLink = links.createNextLink();
		String[] parameterNames = initialCommand.getParameterNames();
		for (int i = 0; i < parameterNames.length; ++i)
			nextLink.setParameters(parameterNames[i], initialCommand
					.getParameterValues(parameterNames[i]));
		nextLink.setParameter(initialCommand.getResultName(), initialCommand
				.getResultValue());
		String[] fields = new String[varMap.size() + variables.length + 2];
		int j = 0;
		for (String key : varMap.keySet())
		{
			fields[j] = key;
			++j;
		}
		System.arraycopy(variables, 0, fields, varMap.size(), variables.length);
		fields[fields.length - 2] = "gvpUserData";
		fields[fields.length - 1] = "gvpCtiC";
		Submit submit = new Submit(nextLink.toString(), fields);
		submit.setMethod("post");
		block.addAction(submit);
		form.addFormElement(block);
		ILink hangupLink = links.createNextLink();
		for (int i = 0; i < parameterNames.length; ++i)
			hangupLink.setParameters(parameterNames[i], initialCommand
					.getParameterValues(parameterNames[i]));
		hangupLink.setParameter(initialCommand.getResultName(),
				initialCommand.getHangupResultValue());
		Catch disconnectCatch = new Catch("connection.disconnect.hangup");
		disconnectCatch.addAction(new Goto(hangupLink.toString()));
		form.addEventHandler(disconnectCatch);
		document.addDialog(form);
		
		List<String> events = ExtendedActionEventManager.getDefault().getExtendedEvents();
		String cpaPrefix = "externalmessage.cpa";
		if(events.contains(cpaPrefix))
		{
			List<String> cpaEvents = new ArrayList<String>();
			for(String event : events)
			{
				if(event.startsWith(cpaPrefix))
					cpaEvents.add(event);
				else
				{
					ILink eventLink = links.createNextLink();
					for (int i = 0; i < parameterNames.length; ++i)
						eventLink.setParameters(parameterNames[i], initialCommand
								.getParameterValues(parameterNames[i]));
					eventLink.setParameter(initialCommand.getResultName(), event);
					Catch eventCatch = new Catch(event);
					eventCatch.addAction(new Goto(eventLink.toString()));
					form.addEventHandler(eventCatch);
				}
			}
			//cpa events
			Catch cpaCatch = new Catch(cpaPrefix);
			
			for(String cpaEvent : cpaEvents)
			{
				if(!cpaPrefix.equals(cpaEvent))
				{
					ILink eventLink = links.createNextLink();
					for (int i = 0; i < parameterNames.length; ++i)
						eventLink.setParameters(parameterNames[i], initialCommand
								.getParameterValues(parameterNames[i]));
					eventLink.setParameter(initialCommand.getResultName(), cpaEvent);
					If eventIf = new If("_event==Õ" + cpaEvent + "Õ");
//					If eventIf = new If("_event=='" + cpaEvent + "'");
					eventIf.addAction(new Goto(eventLink.toString()));
					cpaCatch.addIfClause(eventIf);
				}
			}
			ILink cpaLink = links.createNextLink();
			for (int i = 0; i < parameterNames.length; ++i)
				cpaLink.setParameters(parameterNames[i], initialCommand
						.getParameterValues(parameterNames[i]));
			cpaLink.setParameter(initialCommand.getResultName(), cpaPrefix);
			cpaCatch.addAction(new Goto(cpaLink.toString()));
			form.addEventHandler(cpaCatch);
		}
		else
		{
			for(String event : events)
			{
				ILink eventLink = links.createNextLink();
				for (int i = 0; i < parameterNames.length; ++i)
					eventLink.setParameters(parameterNames[i], initialCommand
							.getParameterValues(parameterNames[i]));
				eventLink.setParameter(initialCommand.getResultName(), event);
				Catch eventCatch = new Catch(event);
				eventCatch.addAction(new Goto(eventLink.toString()));
				form.addEventHandler(eventCatch);
			}
		}
		return document;
	}

    protected IDocument renderMetaDataRequest(ILinkFactory links,
            MetaDataRequestCommand metaDataMessageRequest)
    {
		Form form = new Form("SetAttachedDataForm");
		Send send = new Send();
		send.setAsync(false);
		Receive receive = new Receive();
		receive.setMaxtime("10s");
		StringBuilder nameList = new StringBuilder();
		
		
		String[] names = metaDataMessageRequest.getMetaDataNames();
		
		for(int i = 0; i < names.length; i++)
        {
			String encodedName = "Keyname" + (i+1);
			nameList.append(encodedName);
			nameList.append('=');
			String encodedValue = URLEncoder.encode(names[i]);
			encodedValue = encodedValue.replaceAll("\\+", "%20");
			nameList.append(encodedValue);
			if(i < names.length -1)
				nameList.append('&');
//	        form.addVariable(new Variable(names[i], "'"+metaDataMessageCommand.getMetaDataValue(names[i])+"'"));
//	        if(i != 0)
//	        	nameList.append(' ');
//	        nameList.append(names[i]);
        }
//		send.setNameList(nameList.toString());
		send.setBody(nameList.toString() + "&Action=GetData");
		send.setContentType("application/x-www-form-urlencoded;charset=utf-8");
		
		form.addVariable(new Variable("GetDataMessage", ""));
		
		Block block = new Block("RedirectBlock");
		ILink createNextLink = links.createNextLink();
		createNextLink.setParameter(metaDataMessageRequest.getResultName(), metaDataMessageRequest.getFilledResultValue());
		String[] params = metaDataMessageRequest.getParameterNames();
		for(int i = 0; i < params.length; i++)
        {
			createNextLink.setParameters(params[i], metaDataMessageRequest.getParameterValues(params[i]));
        }

		Script jsonInclude = new Script();
		jsonInclude.setSrc(links.createIncludeLink(Activator.getDefault().getBundle().getSymbolicName() + "/includes/json.js").toString());
		block.addAction(jsonInclude);
		
		send.setGvpPrefix(true);
		receive.setGvpPrefix(true);
		block.addAction(send);
		block.addAction(receive);
		
//		block.addAction(new Assignment("GetDataMessage", "application.lastmessage$.content"));
		block.addAction(new Assignment("GetDataMessage", "JSON.stringify(application.lastmessage$)"));
		
		Submit submit = new Submit(createNextLink.toString(), new String[]{"GetDataMessage"});
		submit.setMethod(METHOD_POST);
		block.addAction(submit);
		form.addFormElement(block);
		ILink hangupLink = links.createNextLink();
		hangupLink.setParameter(metaDataMessageRequest.getResultName(),
				metaDataMessageRequest.getHangupResultValue());
		Catch disconnectCatch = new Catch("connection.disconnect.hangup");
		disconnectCatch.addAction(new Goto(hangupLink.toString()));
		form.addEventHandler(disconnectCatch);
	    return this.createVXMLDocument(links, form);
  	
    	
/*
		Form form = new Form("SetAttachedDataForm");
		UserData userData = new UserData("GetAttachedData");
		userData.setDoGet(true);
		String[] names = metaDataMessageRequest.getMetaDataNames();
		for(int i = 0; i < names.length; i++)
        {
	        userData.addParameter(new Parameter(names[i], "''"));
        }
		String[] parameterNames = metaDataMessageRequest.getParameterNames();
		String[] submitVars = new String[parameterNames.length + 2];
		submitVars[0] = metaDataMessageRequest.getDataName();
		submitVars[1] = metaDataMessageRequest.getResultName();
		Filled filled = new Filled();
		filled.addVariable(new Variable(metaDataMessageRequest.getResultName(), "'" + metaDataMessageRequest.getFilledResultValue() + "'"));
		for (int i = 0; i < parameterNames.length; ++i)
		{
			submitVars[i + 2] = parameterNames[i];
			String[] values = metaDataMessageRequest.getParameterValues(parameterNames[i]);
			StringBuffer buf = new StringBuffer();
			for(int v = 0; v < values.length; v++)
			{
				buf.append(values[v]);
				if(v < values.length - 1)
					buf.append(',');
			}
			Variable paramVar = new Variable(parameterNames[i], "'" + buf.toString() + "'");
			filled.addVariable(paramVar);
		}
		ILink filledLink = links.createNextLink();
		Submit submit = new Submit(filledLink.toString(), submitVars);
		submit.setMethod(VXMLConstants.METHOD_POST);
		submit.setEncodingType("multipart/form-data");
		filled.addAction(submit);
		userData.addFilledHandler(filled);
		form.addFormElement(userData);
		ILink hangupLink = links.createNextLink();
		for (int i = 0; i < parameterNames.length; ++i)
			hangupLink.setParameters(parameterNames[i], metaDataMessageRequest
					.getParameterValues(parameterNames[i]));
		hangupLink.setParameter(metaDataMessageRequest.getResultName(),
				metaDataMessageRequest.getHangupResultValue());
		Catch disconnectCatch = new Catch("connection.disconnect.hangup");
		disconnectCatch.addAction(new Goto(hangupLink.toString()));
		form.addEventHandler(disconnectCatch);
	    return this.createVXMLDocument(links, form);
*/
    }


	protected IDocument renderMetaDataMessage(ILinkFactory links, MetaDataMessageCommand metaDataMessageCommand)
    {
		Form form = new Form("SetAttachedDataForm");
		Send send = new Send();
		send.setAsync(false);
		StringBuilder nameList = new StringBuilder();
		
		
		String[] names = metaDataMessageCommand.getMetaDataNames();
		
		for(int i = 0; i < names.length; i++)
        {
			String encodedName = URLEncoder.encode(names[i]);
			encodedName = encodedName.replaceAll("\\+", "%20");
			nameList.append(encodedName);
			nameList.append('=');
			String encodedValue = URLEncoder.encode(metaDataMessageCommand.getMetaDataValue(names[i]));
			encodedValue = encodedValue.replaceAll("\\+", "%20");
			nameList.append(encodedValue);
			if(i < names.length -1)
				nameList.append('&');
//	        form.addVariable(new Variable(names[i], "'"+metaDataMessageCommand.getMetaDataValue(names[i])+"'"));
//	        if(i != 0)
//	        	nameList.append(' ');
//	        nameList.append(names[i]);
        }
//		send.setNameList(nameList.toString());
		
//		send.setBody(nameList.toString() + (isCtiC ? "&Action=AttachData&sub_action=Add": ""));
		send.setBody(nameList.toString() + (isCtiC ? "&Action=AttachData&sub_action=Replace": ""));
		send.setContentType("application/x-www-form-urlencoded;charset=utf-8");
		Block block = new Block("RedirectBlock");
		ILink createNextLink = links.createNextLink();
		createNextLink.setParameter(metaDataMessageCommand.getResultName(), metaDataMessageCommand.getFilledResultValue());
		String[] params = metaDataMessageCommand.getParameterNames();
		for(int i = 0; i < params.length; i++)
        {
			createNextLink.setParameters(params[i], metaDataMessageCommand.getParameterValues(params[i]));
        }
		block.addAction(send);
		if(isCtiC)
		{
			Receive receive = new Receive();
			receive.setGvpPrefix(true);
			block.addAction(receive);
		}
		block.addAction(new Goto(createNextLink.toString()));
		form.addFormElement(block);
		ILink hangupLink = links.createNextLink();
		hangupLink.setParameter(metaDataMessageCommand.getResultName(),
				metaDataMessageCommand.getHangupResultValue());
		Catch disconnectCatch = new Catch("connection.disconnect.hangup");
		disconnectCatch.addAction(new Goto(hangupLink.toString()));
		form.addEventHandler(disconnectCatch);
	    return this.createVXMLDocument(links, form);
    }


	@Override
    public Map processMetaDataResponse(MetaDataRequestConfiguration configuration,
            IActionContext context)
    {
		Map dataMap = new HashMap();
		//		String attachedDataContent = context.getParameter("GetAttachedData");
		String attachedDataContent = context.getParameter("GetDataMessage");
		
		try
		{
			JsonFactory jsonFactory = new JsonFactory();
			JsonParser jp = jsonFactory.createJsonParser(attachedDataContent);
			Map<String,Object> userData = mapper.readValue(jp, Map.class);

			//Result=SUCCESS&Action=UserDataResp&Sub_Action=AttachData&VH_Result=theResult&vh_transferdestination=someXfer&vht_vis_segment=VHT_Test_Segment
			if(userData.containsKey("content"))
			{
				String contentString = (String)userData.get("content");
				String contentArray[] = contentString.split("&");
				for(String kvpString : contentArray)
				{
					String kvpArray [] = kvpString.split("=", 2);
					dataMap.put(kvpArray[0], URLDecoder.decode((String)kvpArray[1]));
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		/*		
		try
        {
	        ByteArrayInputStream bais = new ByteArrayInputStream(attachedDataContent.getBytes());
	        Document attachedDataDocument = XMLUtilities.getDocumentBuilder().parse(bais);
	        context.debug("AttachedDataDocument: " + attachedDataDocument);
	        NodeList dataList = attachedDataDocument.getDocumentElement().getElementsByTagName("key");
	        for(int i = 0; i < dataList.getLength(); i++)
	        {
	        	Element dataElement = (Element)dataList.item(i);
		        context.debug("KVP received - key: " + dataElement.getAttribute("name") + " value: " + dataElement.getAttribute("value"));
	        	dataMap.put(dataElement.getAttribute("name"), dataElement.getAttribute("value"));
	        }
        }
        catch(Exception e)
        {
	        e.printStackTrace();
        }
*/
		return dataMap;
    }

}
