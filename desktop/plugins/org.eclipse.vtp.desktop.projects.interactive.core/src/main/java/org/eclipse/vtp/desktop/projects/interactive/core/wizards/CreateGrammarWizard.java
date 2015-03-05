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
package org.eclipse.vtp.desktop.projects.interactive.core.wizards;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.internal.resources.File;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.vtp.desktop.model.interactive.core.IMediaContainer;
import org.eclipse.vtp.desktop.model.interactive.core.IMediaFile;
import org.eclipse.vtp.desktop.model.interactive.core.IMediaResource;
import org.eclipse.vtp.desktop.model.interactive.core.internal.MediaFile;
import org.eclipse.vtp.desktop.projects.interactive.core.Activator;
import org.eclipse.vtp.framework.util.Guid;

import com.openmethods.openvxml.desktop.model.businessobjects.IBusinessObject;
import com.openmethods.openvxml.desktop.model.businessobjects.internal.BusinessObject;

/**
 * This wizard walks the user through the steps required to create a new
 * grammar file for an application. The user is prompted to enter a name for the new
 * file. This name must be unique among the current files in the
 * folder. The file is automatically created by this wizard and so
 * requires no actions from the caller of the wizard.
 *
 * @author Trip
 */
public class CreateGrammarWizard extends Wizard implements INewWizard,
IExecutableExtension
{
	/**
	 * The page that collects the file name and grammar type.
	 */
	private GrammarWizardPage gwp = null;
	
	private GrammarFilenameWizardPage gfwp = null;
	
	IConfigurationElement configElement = null;
	
	IFile gramFile;
	
	
	/**
	 * Creates a new <code>CreateDatabaseWizard</code> instance in the
	 * given database set.
	 *
	 * @param mediaContainer The database set that will contain the new database descriptor
	 */
	public CreateGrammarWizard()
	{
		super();
		gwp = new GrammarWizardPage();
		gfwp = new GrammarFilenameWizardPage();
		addPage(gfwp);
		addPage(gwp);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	public void setInitializationData(IConfigurationElement cfig,
		String propertyName, Object data)
	{
		configElement = cfig;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchWizard#init(org.eclipse.ui.IWorkbench, org.eclipse.jface.viewers.IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection)
	{
	}
	
	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jface.wizard.IWizard#performFinish()
	 */
	public boolean performFinish()
	{
		try
		{
			if(gwp.voiceGrammarButton.getSelection())
			{
				gramFile.create(getClass().getClassLoader().getResourceAsStream("voice_grammar_template.grxml"), true, null);
			}
			else
			{
				if(gwp.dtmfTemplateButton.getSelection())
				{
					InputStream tempin = Activator.class.getClassLoader().getResourceAsStream("dtmf_grammar_gen_template.grxml");
					StringBuffer text = new StringBuffer();
					byte[] buf = new byte[10240];
					int len = tempin.read(buf);
					while(len != -1)
					{
						text.append(new String(buf, 0, len));
						len = tempin.read(buf);
					}
					String contents = text.toString();
					contents = contents.replaceAll("\\[min\\]", gwp.minDigitsField.getText());
					contents = contents.replaceAll("\\[max\\]", gwp.maxDigitsField.getText());
					gramFile.create(new ByteArrayInputStream(contents.getBytes()), true, null);
				}
				else
					gramFile.create(getClass().getClassLoader().getResourceAsStream("dtmf_grammar_template.grxml"), true, null);
			}
			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return false;
	}

	private class GrammarWizardPage extends WizardPage
	{
		Text grammarNameField = null;
		Button voiceGrammarButton = null;
		Button dtmfGrammarButton = null;
		Button dtmfTemplateButton = null;
		Text minDigitsField = null;
		Text maxDigitsField = null;
		
		public GrammarWizardPage()
		{
			super("GrammarPage", "Enter a name for the grammar file", null);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
		 */
		public void createControl(Composite parent)
		{
			Composite comp = new Composite(parent, SWT.NONE);
			comp.setLayout(new FormLayout());

			Label grammarNameLabel = new Label(comp, SWT.NONE);
			grammarNameLabel.setText("Grammar File Name:");
			grammarNameLabel.setSize(grammarNameLabel.computeSize(SWT.DEFAULT,
					SWT.DEFAULT));
			FormData brandNameLabelData = new FormData();
			brandNameLabelData.left = new FormAttachment(0, 10);
			brandNameLabelData.top = new FormAttachment(0, 30);
			brandNameLabelData.right = new FormAttachment(0,
					10 + grammarNameLabel.getSize().x);
			brandNameLabelData.bottom = new FormAttachment(0,
					30 + grammarNameLabel.getSize().y);
			grammarNameLabel.setLayoutData(brandNameLabelData);
			
			grammarNameField = new Text(comp, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			
			FormData brandNameFieldData = new FormData();
			brandNameFieldData.left = new FormAttachment(grammarNameLabel, 10);
			brandNameFieldData.top = new FormAttachment(0, 29);
			brandNameFieldData.right = new FormAttachment(100, -10);
			grammarNameField.setLayoutData(brandNameFieldData);
			grammarNameField.setEnabled(false);
			grammarNameField.setText("");
			
			voiceGrammarButton = new Button(comp, SWT.RADIO);
			voiceGrammarButton.setText("Voice Grammar");
			voiceGrammarButton.setSelection(true);
			FormData voiceButtonData = new FormData();
			voiceButtonData.left = new FormAttachment(0, 10);
			voiceButtonData.top = new FormAttachment(grammarNameLabel, 20);
			voiceButtonData.right = new FormAttachment(100, -10);
			voiceGrammarButton.setLayoutData(voiceButtonData);
			
			dtmfGrammarButton = new Button(comp, SWT.RADIO);
			dtmfGrammarButton.setText("DTMF Grammar");
			FormData dtmfButtonData = new FormData();
			dtmfButtonData.left = new FormAttachment(0, 10);
			dtmfButtonData.top = new FormAttachment(voiceGrammarButton, 20);
			dtmfButtonData.right = new FormAttachment(100, -10);
			dtmfGrammarButton.setLayoutData(dtmfButtonData);

			dtmfTemplateButton = new Button(comp, SWT.CHECK);
			dtmfTemplateButton.setText("Use this template");
			FormData dtmfTemplateData = new FormData();
			dtmfTemplateData.left = new FormAttachment(0, 30);
			dtmfTemplateData.top = new FormAttachment(dtmfGrammarButton, 15);
			dtmfTemplateData.right = new FormAttachment(100, -10);
			dtmfTemplateButton.setLayoutData(dtmfTemplateData);
			
			Label minDigitsLabel = new Label(comp, SWT.NONE);
			minDigitsLabel.setText("Minimum Digits");
			FormData minDigitsLabelData = new FormData();
			minDigitsLabelData.left = new FormAttachment(0, 50);
			minDigitsLabelData.top = new FormAttachment(dtmfTemplateButton, 15);
			minDigitsLabel.setLayoutData(minDigitsLabelData);
			
			minDigitsField = new Text(comp, SWT.BORDER);
			FormData minDigitsFieldData = new FormData();
			minDigitsFieldData.left = new FormAttachment(minDigitsLabel, 20);
			minDigitsFieldData.top = new FormAttachment(dtmfTemplateButton, 15);
			minDigitsFieldData.right = new FormAttachment(minDigitsLabel, 50, SWT.RIGHT);
			minDigitsField.setLayoutData(minDigitsFieldData);
			minDigitsField.setEnabled(false);
			
			Label maxDigitsLabel = new Label(comp, SWT.NONE);
			maxDigitsLabel.setText("Maximum Digits");
			FormData maxDigitsLabelData = new FormData();
			maxDigitsLabelData.left = new FormAttachment(0, 50);
			maxDigitsLabelData.top = new FormAttachment(minDigitsLabel, 15);
			maxDigitsLabel.setLayoutData(maxDigitsLabelData);
			
			maxDigitsField = new Text(comp, SWT.BORDER);
			FormData maxDigitsFieldData = new FormData();
			maxDigitsFieldData.left = new FormAttachment(maxDigitsLabel, 20);
			maxDigitsFieldData.top = new FormAttachment(minDigitsLabel, 15);
			maxDigitsFieldData.right = new FormAttachment(maxDigitsLabel, 50, SWT.RIGHT);
			maxDigitsField.setLayoutData(maxDigitsFieldData);
			maxDigitsField.setEnabled(false);
			
			dtmfTemplateButton.addSelectionListener(new SelectionListener()
			{
				public void widgetDefaultSelected(SelectionEvent e)
                {
                }

				public void widgetSelected(SelectionEvent e)
                {
					minDigitsField.setEnabled(dtmfTemplateButton.getSelection());
					maxDigitsField.setEnabled(dtmfTemplateButton.getSelection());
                }
			});
			
			setControl(comp);
		}
	}

	private class GrammarFilenameWizardPage extends WizardPage
	{
		private static final String MESSAGE_NO_FOLDER_SELECTED = "Please select a folder for this grammar file.";
		private static final String MESSAGE_EMPTY_FILENAME = "Filename cannot be empty";
		private static final String MESSAGE_FILENAME_TAKEN = "A file already exists with that name.";
		
		Text filenameField = null;
		Tree tree = null;
		
		private List<String> takenURIs = new ArrayList<String>();
		
		public GrammarFilenameWizardPage()
		{
			super("GrammarFilenamePage",
				"Select a project, folder, and filename for the new grammar file", null);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
		 */
		public void createControl(Composite parent)
		{
			setPageComplete(false);

			//base composite to build on
			Composite comp = new Composite(parent, SWT.NONE);
			
			tree = createProjSelector(comp);
			
			//name label
			Label filenameLabel = new Label(comp, SWT.NONE);
			filenameLabel.setText("Filename:");
			filenameLabel.setSize(filenameLabel.computeSize(SWT.DEFAULT,
					SWT.DEFAULT));
			
			//name field
			filenameField = new Text(comp, SWT.SINGLE | SWT.BORDER);
			
			filenameField.addVerifyListener(new VerifyListener()
			{

				public void verifyText(VerifyEvent e)
                {
	                String text = e.text;
	                char[] chars = text.toCharArray();
					String currentName = filenameField.getText().substring(0, e.start) + e.text + filenameField.getText(e.end, (filenameField.getText().length() - 1));
					
	                if(currentName.length() > 255)
	                {
	                	e.doit = false;
	                	return;
	                }
	                for(int i = 0; i < chars.length; i++)
	                {
	                	if(Character.isLetterOrDigit(chars[i]))
	                		continue;
	                	if(chars[i] == '$')
	                		continue;
	                	if(chars[i] == '_')
	                		continue;
	                	if(chars[i] == '.')
	                		continue;
	                	e.doit = false;
	                	return;
	                }
	                
                }
				
			});
			filenameField.addModifyListener(new ModifyListener()
				{
					public void modifyText(ModifyEvent e)
					{
						if(!isVoiceProjectFolderSelected())
						{
							setErrorMessage(MESSAGE_NO_FOLDER_SELECTED);
							setPageComplete(false);
							return;
						}
						
						TreeItem ti = tree.getSelection()[0];
						IResource res = (IResource)ti.getData();
						
						if(ti.getText(1).endsWith("file"))
						{
							tree.deselect(ti);
							tree.select(ti.getParentItem());
						}
						
						if(filenameField.getText() == null || filenameField.getText().equals(""))
						{
							setErrorMessage(MESSAGE_EMPTY_FILENAME);
							setPageComplete(false);
							return;
						}
						
						if(!isFilenameAvailable())
						{
							setErrorMessage(MESSAGE_FILENAME_TAKEN);
							setPageComplete(false);
							return;
						}

						String filename = filenameField.getText(); 
						if(filename != null && !filename.endsWith(".grxml"))
							filename += ".grxml";
						
						IPath folderPath = res.getLocation();
						IPath lastSegment = folderPath.removeFirstSegments(folderPath.segmentCount() - 1);
						
						gramFile = res.getParent().getFolder(lastSegment).getFile(filename); 
						gwp.grammarNameField.setText(gramFile.getFullPath().toString());
						
						setErrorMessage(null);
						setPageComplete(true);
					}
				});
			
			//Layout section
			comp.setLayout(new FormLayout());

			FormData treeData = new FormData();
			treeData.left = new FormAttachment(0, 10);
			treeData.top = new FormAttachment(0, 30);
			treeData.right = new FormAttachment(100, 10);
			treeData.bottom = new FormAttachment(75,30);
			tree.setLayoutData(treeData);

			FormData boNameLabelData = new FormData();
			boNameLabelData.left = new FormAttachment(0, 10);
			boNameLabelData.top = new FormAttachment(tree, 30);
			boNameLabelData.right = new FormAttachment(0, 10 + filenameLabel.getSize().x);
			boNameLabelData.bottom = new FormAttachment(100, 30 + filenameLabel.getSize().y);
			filenameLabel.setLayoutData(boNameLabelData);

			FormData boNameFieldData = new FormData();
			boNameFieldData.left = new FormAttachment(filenameLabel, 10);
			boNameFieldData.top = new FormAttachment(tree, 30);
			boNameFieldData.right = new FormAttachment(100, 10);
			boNameLabelData.bottom = new FormAttachment(100, 30 + filenameField.getSize().y);
			filenameField.setLayoutData(boNameFieldData);
				
			setControl(comp);
		}
		
		/**
		 * Creates a Tree to use for viewing available projects and selecting which
		 * Workflow will contain the new Business Object
		 * 
		 * @param comp - the Composite on which to put the Tree
		 * @return a reference to the Tree
		 */
		private Tree createProjSelector(Composite comp)
		{
			takenURIs.clear();
			
			//create the tree
			final Tree tree = new Tree(comp, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
			tree.setHeaderVisible(true);

			tree.addSelectionListener(new SelectionListener()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					TreeItem ti = tree.getSelection()[0];
					IResource res = (IResource)ti.getData();
					
					if(ti.getText(1).endsWith("file"))
					{
						setErrorMessage(MESSAGE_FILENAME_TAKEN);
						filenameField.setText(res.getName());
						setPageComplete(false);
						return;
					}
					
					
					if(!isVoiceProjectFolderSelected()) 
					{
						setErrorMessage(MESSAGE_NO_FOLDER_SELECTED);
						setPageComplete(false);
						return;
					}
					
					if(filenameField.getText() == null || filenameField.getText().equals(""))
					{
						setErrorMessage(MESSAGE_EMPTY_FILENAME);
						setPageComplete(false);
						return;
					}
					
					if(!isFilenameAvailable())
					{
						setErrorMessage(MESSAGE_FILENAME_TAKEN);
						setPageComplete(false);
						return;
					}

					setErrorMessage(null);
					setPageComplete(true);
					
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e)
				{
					Object srcObj = e.getSource();
					if(!(srcObj instanceof Tree))
						return;
					
					Tree tree = (Tree)srcObj;
					if(tree.getSelectionCount() != 1)
						return;
					
					TreeItem selection = tree.getSelection()[0];
					if(selection.getItems().length > 0)
						selection.setExpanded(!selection.getExpanded());
				}
			});
			
			//set up the columns
			TreeColumn nameColumn = new TreeColumn(tree, SWT.LEFT);
			nameColumn.setText("Name");
			nameColumn.setWidth(450);
			TreeColumn boColumn = new TreeColumn(tree, SWT.RIGHT);
			boColumn.setText("Type");
			boColumn.setWidth(150);

			
			IProject[] projs = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			for(IProject proj : projs)
			{
				String projType = getProjectType(proj);
				if ("Voice".equals(projType))
				{
					TreeItem ti = new TreeItem(tree, SWT.NONE);
					ti.setText(new String[]{proj.getName(), projType});

					
					IResource[] members;
					try {
						members = proj.members();
						for(IResource member : members)
						{
							if(!(member.isHidden() || member.getName().startsWith(".")))
								resourceToTreeItem(proj, member, ti, SWT.NONE);
							else
								//This is already done inside the resourceToTreeItem method, so this takes care of the hidden or "." files
								takenURIs.add(member.getFullPath().toString()); 
						}
					} catch (CoreException e) {
						e.printStackTrace();
						return null;
					}
				}
				else
				{
					TreeItem ti = new TreeItem(tree, SWT.NONE);
					ti.setText(new String[]{proj.getName(), projType});
				}
			}			
			return tree;
		}

		private TreeItem resourceToTreeItem(IProject proj, IResource res, TreeItem parent, int style)
		{
			TreeItem ti = new TreeItem(parent, style);
			
			takenURIs.add(res.getFullPath().toString());
			
			if(res.getType() == IResource.FOLDER) //Folder
			{
				ti.setText(new String[]{res.getName(), "Folder"});
				
				IResource[] members;
				try {
					members = proj.getFolder(res.getProjectRelativePath()).members();
					for(IResource member : members)
					{
						if(!(member.isHidden() || member.getName().startsWith(".")))
							resourceToTreeItem(proj, member, ti, style);
					}
				} catch (CoreException e) {
					e.printStackTrace();
					return null;
				}
			}
			else //Not a folder
			{
				ti.setText(new String[]{res.getName(), res.getFileExtension() + " file", res.getName()});
				ti.setGrayed(true);
			}
			ti.setData(res);
			return ti;
		}
		
		private String getProjectType(IProject proj)
		{
			//set up natures for project type recognition and display
			String workflowNature = org.eclipse.vtp.desktop.model.core.natures.WorkflowProjectNature.NATURE_ID;
			String workflowNature5 = org.eclipse.vtp.desktop.model.core.natures.WorkflowProjectNature5_0.NATURE_ID;
			String applicationNature = org.eclipse.vtp.desktop.projects.core.builder.VoiceApplicationNature.NATURE_ID;//legacy type
			String fragmentNature = org.eclipse.vtp.desktop.projects.core.builder.VoiceApplicationFragmentNature.NATURE_ID;//legacy type
			
			//TODO fix project setup and access the static NATURE_ID for these
//			String voiceNature = org.eclipse.vtp.desktop.model.interactive.voice.natures.VoiceProjectNature.NATURE_ID;
//			String voiceNature5 = org.eclipse.vtp.desktop.model.interactive.voice.natures.VoiceProjectNature5_0.NATURE_ID;
//			String personaNature = org.eclipse.vtp.desktop.projects.voice.builder.VoicePersonaNature.NATURE_ID;//legacy type
			String voiceNature = "org.eclipse.vtp.desktop.model.interactive.voice.VoiceProjectNature";
			String voiceNature5 = "org.eclipse.vtp.desktop.model.interactive.voice.VoiceProjectNature5_0";
			String personaNature = "org.eclipse.vtp.desktop.projects.voice.VoicePersonaNature";//legacy type
			
			//return the project type
			try{
			if(proj.getNature(workflowNature)!= null)
				return "Workflow";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(workflowNature5)!= null)
				return "Workflow";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(voiceNature)!= null)
				return"Voice";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(voiceNature5)!= null)
				return "Voice";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(applicationNature)!= null)
				return "(Legacy) Application";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(fragmentNature)!= null)
				return "(Legacy) Fragment";
			}catch(Exception e){e.printStackTrace();}
			try{
			if(proj.getNature(personaNature)!= null)
				return "(Legacy) Voice";
			}catch(Exception e){e.printStackTrace();}

			return "";
		}
		
		/**
		 * 
		 * @return true if a folder inside a voice project is selected
		 */
		private boolean isVoiceProjectFolderSelected()
		{
			if(tree.getSelection().length == 0)
				return false;
			return "Folder".equals(tree.getSelection()[0].getText(1));
		}
		
		/**
		 * 
		 * @return true if a folder in a voice project is selected and the
		 * proposed name is neither empty nor taken
		 */
		private boolean isFilenameAvailable()
		{
			String proposedFilename = filenameField.getText();
			if(proposedFilename == null || proposedFilename.equals(""))
				return false;
			
			TreeItem selection = tree.getSelection()[0];
			IResource res = (IResource)selection.getData();
			String proposedPath = res.getFullPath().toString();
			
			if(takenURIs.contains(proposedPath + "/" + proposedFilename) || 
					takenURIs.contains(proposedPath + "/" + proposedFilename + ".grxml"))
				return false;
			
			return true;
		}
		
		
	}

}
