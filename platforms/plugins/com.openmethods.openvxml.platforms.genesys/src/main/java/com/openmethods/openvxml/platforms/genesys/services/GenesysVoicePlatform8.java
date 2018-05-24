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
			
			originalValue = dirtyStringReplaceGreek(originalValue);
			
			System.out.println("gvpUserData Encoded with greek: " + originalValue);
			
			originalValue = URLDecoder.decode(originalValue);
			
			
			System.out.println("gvpUserData Decoded with greek: " + originalValue);
			
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
					If eventIf = new If("_event==�" + cpaEvent + "�");
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
			String encodedValue = URLEncoder.encode(URLDecoder.decode(URLDecoder.decode(metaDataMessageCommand.getMetaDataValue(names[i]))));
			System.out.println("FINAL VALUE: "+ names[i] + " " + encodedValue);
			
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
	
	private String dirtyStringReplaceGreek(String original)
	{
		String encoded  = URLEncoder.encode(original); 
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%98", URLEncoder.encode("%CE%91"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B1", URLEncoder.encode("%CE%B1"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%99", URLEncoder.encode("%CE%92"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B2", URLEncoder.encode("%CE%B2"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%9C", URLEncoder.encode("%CE%93"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B3", URLEncoder.encode("%CE%B3"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%9D", URLEncoder.encode("%CE%94"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B4", URLEncoder.encode("%CE%B4"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%A2", URLEncoder.encode("%CE%95"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B5", URLEncoder.encode("%CE%B5"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%93", URLEncoder.encode("%CE%96"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B6", URLEncoder.encode("%CE%B6"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%94", URLEncoder.encode("%CE%97"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B7", URLEncoder.encode("%CE%B7"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%8B%C2%9C", URLEncoder.encode("%CE%98"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B8", URLEncoder.encode("%CE%B8"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%84%C2%A2", URLEncoder.encode("%CE%99"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B9", URLEncoder.encode("%CE%B9"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%A1", URLEncoder.encode("%CE%9A"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BA", URLEncoder.encode("%CE%BA"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%BA", URLEncoder.encode("%CE%9B"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BB", URLEncoder.encode("%CE%BB"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%93", URLEncoder.encode("%CE%9C"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BC", URLEncoder.encode("%CE%BC"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%5Cu009d", URLEncoder.encode("%CE%9D"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BD", URLEncoder.encode("%CE%BD"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%BE", URLEncoder.encode("%CE%9E"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BE", URLEncoder.encode("%CE%BE"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%B8", URLEncoder.encode("%CE%9F"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%BF", URLEncoder.encode("%CE%BF"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A0", URLEncoder.encode("%CE%A0"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%82%C2%AC", URLEncoder.encode("%CF%80"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A1", URLEncoder.encode("%CE%A1"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%5Cu0081", URLEncoder.encode("%CF%81"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A3", URLEncoder.encode("%CE%A3"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%86%C2%92", URLEncoder.encode("%CF%83"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%9A", URLEncoder.encode("%CF%82"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A4", URLEncoder.encode("%CE%A4"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%9E", URLEncoder.encode("%CF%84"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A5", URLEncoder.encode("%CE%A5"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%A6", URLEncoder.encode("%CF%85"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A6", URLEncoder.encode("%CE%A6"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%A0", URLEncoder.encode("%CF%86"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A7", URLEncoder.encode("%CE%A7 "));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%A1", URLEncoder.encode("%CF%87"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A8", URLEncoder.encode("%CE%A8"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%8B%C2%86", URLEncoder.encode("%CF%88"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%A9", URLEncoder.encode("%CE%A9"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%B0", URLEncoder.encode("%CF%89"));		
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%A0", URLEncoder.encode("%CE%86"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%AC", URLEncoder.encode("%CE%AC"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%8B%C2%86", URLEncoder.encode("%CE%88"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%5Cu00ad", URLEncoder.encode("%CE%AD"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%A2%C2%80%C2%B0", URLEncoder.encode("%CE%89"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%AE", URLEncoder.encode("%CE%AE"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%A0", URLEncoder.encode("%CE%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%AF", URLEncoder.encode("%CE%AF"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%92", URLEncoder.encode("%CE%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%85%C2%92", URLEncoder.encode("%CF%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%85%C2%BD", URLEncoder.encode("%CE%8E"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%5Cu008d", URLEncoder.encode("%CF%8D"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%5Cu008f", URLEncoder.encode("%CE%8F"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%85%C2%BD", URLEncoder.encode("%CF%8E"));		
		encoded = encoded.replaceAll("%C3%83%C2%8E%5Cu0090", URLEncoder.encode("%CE%90"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%B0", URLEncoder.encode("%CE%B0"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%AA", URLEncoder.encode("%CE%AA"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%85%C2%A0", URLEncoder.encode("%CF%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%8E%C3%82%C2%AB", URLEncoder.encode("%CE%AB"));
		encoded = encoded.replaceAll("%C3%83%C2%8F%C3%A2%C2%80%C2%B9", URLEncoder.encode("%CF%8B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%82%C2%AC", URLEncoder.encode("%E1%BC%80"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu0081", URLEncoder.encode("%E1%BC%81"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%9A", URLEncoder.encode("%E1%BC%82"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%86%C2%92", URLEncoder.encode("%E1%BC%83"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%9E", URLEncoder.encode("%E1%BC%84"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%A6", URLEncoder.encode("%E1%BC%85"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%A0", URLEncoder.encode("%E1%BC%86"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%A1", URLEncoder.encode("%E1%BC%87"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%8B%C2%86", URLEncoder.encode("%E1%BC%88"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%B0", URLEncoder.encode("%E1%BC%89"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%85%C2%A0", URLEncoder.encode("%E1%BC%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%B9", URLEncoder.encode("%E1%BC%8B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%85%C2%92", URLEncoder.encode("%E1%BC%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu008d", URLEncoder.encode("%E1%BC%8D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%85%C2%BD", URLEncoder.encode("%E1%BC%8E"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu008f", URLEncoder.encode("%E1%BC%8F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu0090", URLEncoder.encode("%E1%BC%90"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%98", URLEncoder.encode("%E1%BC%91"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%99", URLEncoder.encode("%E1%BC%92"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%9C", URLEncoder.encode("%E1%BC%93"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%9D", URLEncoder.encode("%E1%BC%94"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%A2", URLEncoder.encode("%E1%BC%95"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%8B%C2%9C", URLEncoder.encode("%E1%BC%98"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%84%C2%A2", URLEncoder.encode("%E1%BC%99"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%85%C2%A1", URLEncoder.encode("%E1%BC%9A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%A2%C2%80%C2%BA", URLEncoder.encode("%E1%BC%9B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%85%C2%93", URLEncoder.encode("%E1%BC%9C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu009d", URLEncoder.encode("%E1%BC%9D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A0", URLEncoder.encode("%E1%BC%A0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A1", URLEncoder.encode("%E1%BC%A1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A2", URLEncoder.encode("%E1%BC%A2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A3", URLEncoder.encode("%E1%BC%A3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A4", URLEncoder.encode("%E1%BC%A4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A5", URLEncoder.encode("%E1%BC%A5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A6", URLEncoder.encode("%E1%BC%A6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A7", URLEncoder.encode("%E1%BC%A7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A8", URLEncoder.encode("%E1%BC%A8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%A9", URLEncoder.encode("%E1%BC%A9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%AA", URLEncoder.encode("%E1%BC%AA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%AB", URLEncoder.encode("%E1%BC%AB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%AC", URLEncoder.encode("%E1%BC%AC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%5Cu00ad", URLEncoder.encode("%E1%BC%AD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%AE", URLEncoder.encode("%E1%BC%AE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%AF", URLEncoder.encode("%E1%BC%AF"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B0", URLEncoder.encode("%E1%BC%B0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B1", URLEncoder.encode("%E1%BC%B1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B2", URLEncoder.encode("%E1%BC%B2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B3", URLEncoder.encode("%E1%BC%B3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B4", URLEncoder.encode("%E1%BC%B4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B5", URLEncoder.encode("%E1%BC%B5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B6", URLEncoder.encode("%E1%BC%B6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B7", URLEncoder.encode("%E1%BC%B7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B8", URLEncoder.encode("%E1%BC%B8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%B9", URLEncoder.encode("%E1%BC%B9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BA", URLEncoder.encode("%E1%BC%BA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BB", URLEncoder.encode("%E1%BC%BB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BC", URLEncoder.encode("%E1%BC%BC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BD", URLEncoder.encode("%E1%BC%BD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BE", URLEncoder.encode("%E1%BC%BE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BC%C3%82%C2%BF", URLEncoder.encode("%E1%BC%BF"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%82%C2%AC", URLEncoder.encode("%E1%BD%80"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%5Cu0081", URLEncoder.encode("%E1%BD%81"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%9A", URLEncoder.encode("%E1%BD%82"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%86%C2%92", URLEncoder.encode("%E1%BD%83"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%9E", URLEncoder.encode("%E1%BD%84"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%A6", URLEncoder.encode("%E1%BD%85"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%8B%C2%86", URLEncoder.encode("%E1%BD%88"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%B0", URLEncoder.encode("%E1%BD%89"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%85%C2%A0", URLEncoder.encode("%E1%BD%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%B9", URLEncoder.encode("%E1%BD%8B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%85%C2%92", URLEncoder.encode("%E1%BD%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%5Cu008d", URLEncoder.encode("%E1%BD%8D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%5Cu0090", URLEncoder.encode("%E1%BD%90"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%98", URLEncoder.encode("%E1%BD%91"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%99", URLEncoder.encode("%E1%BD%92"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%9C", URLEncoder.encode("%E1%BD%93"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%9D", URLEncoder.encode("%E1%BD%94"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%A2", URLEncoder.encode("%E1%BD%95"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%93", URLEncoder.encode("%E1%BD%96"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%94", URLEncoder.encode("%E1%BD%97"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%84%C2%A2", URLEncoder.encode("%E1%BD%99"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%A2%C2%80%C2%BA", URLEncoder.encode("%E1%BD%9B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%5Cu009d", URLEncoder.encode("%E1%BD%9D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%85%C2%B8", URLEncoder.encode("%E1%BD%9F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A0", URLEncoder.encode("%E1%BD%A0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A1", URLEncoder.encode("%E1%BD%A1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A2", URLEncoder.encode("%E1%BD%A2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A3", URLEncoder.encode("%E1%BD%A3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A4", URLEncoder.encode("%E1%BD%A4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A5", URLEncoder.encode("%E1%BD%A5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A6", URLEncoder.encode("%E1%BD%A6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A7", URLEncoder.encode("%E1%BD%A7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A8", URLEncoder.encode("%E1%BD%A8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%A9", URLEncoder.encode("%E1%BD%A9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%AA", URLEncoder.encode("%E1%BD%AA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%AB", URLEncoder.encode("%E1%BD%AB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%AC", URLEncoder.encode("%E1%BD%AC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%5Cu00ad", URLEncoder.encode("%E1%BD%AD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%AE", URLEncoder.encode("%E1%BD%AE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%AF", URLEncoder.encode("%E1%BD%AF"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B0", URLEncoder.encode("%E1%BD%B0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B1", URLEncoder.encode("%E1%BD%B1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B2", URLEncoder.encode("%E1%BD%B2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B3", URLEncoder.encode("%E1%BD%B3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B4", URLEncoder.encode("%E1%BD%B4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B5", URLEncoder.encode("%E1%BD%B5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B6", URLEncoder.encode("%E1%BD%B6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B7", URLEncoder.encode("%E1%BD%B7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B8", URLEncoder.encode("%E1%BD%B8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%B9", URLEncoder.encode("%E1%BD%B9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%BA", URLEncoder.encode("%E1%BD%BA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%BB", URLEncoder.encode("%E1%BD%BB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%BC", URLEncoder.encode("%E1%BD%BC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BD%C3%82%C2%BD", URLEncoder.encode("%E1%BD%BD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%82%C2%AC", URLEncoder.encode("%E1%BE%80"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu0081", URLEncoder.encode("%E1%BE%81"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%9A", URLEncoder.encode("%E1%BE%82"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%86%C2%92", URLEncoder.encode("%E1%BE%83"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%9E", URLEncoder.encode("%E1%BE%84"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%A6", URLEncoder.encode("%E1%BE%85"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%A0", URLEncoder.encode("%E1%BE%86"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%A1", URLEncoder.encode("%E1%BE%87"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%8B%C2%86", URLEncoder.encode("%E1%BE%88"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%B0", URLEncoder.encode("%E1%BE%89"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%A0", URLEncoder.encode("%E1%BE%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%B9", URLEncoder.encode("%E1%BE%8B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%92", URLEncoder.encode("%E1%BE%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu008d", URLEncoder.encode("%E1%BE%8D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%BD", URLEncoder.encode("%E1%BE%8E"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu008f", URLEncoder.encode("%E1%BE%8F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu0090", URLEncoder.encode("%E1%BE%90"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%98", URLEncoder.encode("%E1%BE%91"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%99", URLEncoder.encode("%E1%BE%92"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%9C", URLEncoder.encode("%E1%BE%93"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%9D", URLEncoder.encode("%E1%BE%94"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%A2", URLEncoder.encode("%E1%BE%95"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%93", URLEncoder.encode("%E1%BE%96"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%94", URLEncoder.encode("%E1%BE%97"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%8B%C2%9C", URLEncoder.encode("%E1%BE%98"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%84%C2%A2", URLEncoder.encode("%E1%BE%99"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%A1", URLEncoder.encode("%E1%BE%9A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%A2%C2%80%C2%BA", URLEncoder.encode("%E1%BE%9B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%93", URLEncoder.encode("%E1%BE%9C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu009d", URLEncoder.encode("%E1%BE%9D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%BE", URLEncoder.encode("%E1%BE%9E"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%85%C2%B8", URLEncoder.encode("%E1%BE%9F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A0", URLEncoder.encode("%E1%BE%A0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A1", URLEncoder.encode("%E1%BE%A1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A2", URLEncoder.encode("%E1%BE%A2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A3", URLEncoder.encode("%E1%BE%A3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A4", URLEncoder.encode("%E1%BE%A4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A5", URLEncoder.encode("%E1%BE%A5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A6", URLEncoder.encode("%E1%BE%A6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A7", URLEncoder.encode("%E1%BE%A7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A8", URLEncoder.encode("%E1%BE%A8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%A9", URLEncoder.encode("%E1%BE%A9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%AA", URLEncoder.encode("%E1%BE%AA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%AB", URLEncoder.encode("%E1%BE%AB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%AC", URLEncoder.encode("%E1%BE%AC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%5Cu00ad", URLEncoder.encode("%E1%BE%AD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%AE", URLEncoder.encode("%E1%BE%AE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%AF", URLEncoder.encode("%E1%BE%AF"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B0", URLEncoder.encode("%E1%BE%B0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B1", URLEncoder.encode("%E1%BE%B1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B2", URLEncoder.encode("%E1%BE%B2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B3", URLEncoder.encode("%E1%BE%B3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B4", URLEncoder.encode("%E1%BE%B4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B6", URLEncoder.encode("%E1%BE%B6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B7", URLEncoder.encode("%E1%BE%B7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B8", URLEncoder.encode("%E1%BE%B8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%B9", URLEncoder.encode("%E1%BE%B9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BA", URLEncoder.encode("%E1%BE%BA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BB", URLEncoder.encode("%E1%BE%BB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BC", URLEncoder.encode("%E1%BE%BC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BD", URLEncoder.encode("%E1%BE%BD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BE", URLEncoder.encode("%E1%BE%BE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BE%C3%82%C2%BF", URLEncoder.encode("%E1%BE%BF"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%82%C2%AC", URLEncoder.encode("%E1%BF%80"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu0081", URLEncoder.encode("%E1%BF%81"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%9A", URLEncoder.encode("%E1%BF%82"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%86%C2%92", URLEncoder.encode("%E1%BF%83"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%9E", URLEncoder.encode("%E1%BF%84"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%A0", URLEncoder.encode("%E1%BF%86"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%A1", URLEncoder.encode("%E1%BF%87"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%8B%C2%86", URLEncoder.encode("%E1%BF%88"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%B0", URLEncoder.encode("%E1%BF%89"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%A0", URLEncoder.encode("%E1%BF%8A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%B9", URLEncoder.encode("%E1%BF%8B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%92", URLEncoder.encode("%E1%BF%8C"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu008d", URLEncoder.encode("%E1%BF%8D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%BD", URLEncoder.encode("%E1%BF%8E"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu008f", URLEncoder.encode("%E1%BF%8F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu0090", URLEncoder.encode("%E1%BF%90"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%98", URLEncoder.encode("%E1%BF%91"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%99", URLEncoder.encode("%E1%BF%92"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%9C", URLEncoder.encode("%E1%BF%93"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%93", URLEncoder.encode("%E1%BF%96"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%94", URLEncoder.encode("%E1%BF%97"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%8B%C2%9C", URLEncoder.encode("%E1%BF%98"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%84%C2%A2", URLEncoder.encode("%E1%BF%99"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%A1", URLEncoder.encode("%E1%BF%9A"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%A2%C2%80%C2%BA", URLEncoder.encode("%E1%BF%9B"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu009d", URLEncoder.encode("%E1%BF%9D"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%BE", URLEncoder.encode("%E1%BF%9E"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%85%C2%B8", URLEncoder.encode("%E1%BF%9F"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A0", URLEncoder.encode("%E1%BF%A0"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A1", URLEncoder.encode("%E1%BF%A1"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A2", URLEncoder.encode("%E1%BF%A2"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A3", URLEncoder.encode("%E1%BF%A3"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A4", URLEncoder.encode("%E1%BF%A4"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A5", URLEncoder.encode("%E1%BF%A5"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A6", URLEncoder.encode("%E1%BF%A6"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A7", URLEncoder.encode("%E1%BF%A7"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A8", URLEncoder.encode("%E1%BF%A8"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%A9", URLEncoder.encode("%E1%BF%A9"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%AA", URLEncoder.encode("%E1%BF%AA"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%AB", URLEncoder.encode("%E1%BF%AB"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%AC", URLEncoder.encode("%E1%BF%AC"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%5Cu00ad", URLEncoder.encode("%E1%BF%AD"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%AE", URLEncoder.encode("%E1%BF%AE"));
		encoded = encoded.replaceAll("%C3%83%C2%A1%C3%82%C2%BF%C3%82%C2%AF", URLEncoder.encode("%E1%BF%AF"));

		return encoded;
	}

}
