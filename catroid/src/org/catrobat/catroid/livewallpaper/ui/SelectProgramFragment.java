/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.livewallpaper.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.common.ProjectData;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.exceptions.CompatibilityProjectException;
import org.catrobat.catroid.exceptions.LoadingProjectException;
import org.catrobat.catroid.exceptions.OutdatedVersionProjectException;
import org.catrobat.catroid.io.SoundManager;
import org.catrobat.catroid.io.StorageHandler;
import org.catrobat.catroid.livewallpaper.LiveWallpaper;
import org.catrobat.catroid.livewallpaper.ProjectLoadableEnum;
import org.catrobat.catroid.livewallpaper.ProjectManagerState;
import org.catrobat.catroid.ui.MyProjectsActivity;
import org.catrobat.catroid.ui.adapter.ProjectAdapter;
import org.catrobat.catroid.ui.adapter.ProjectAdapter.OnProjectEditListener;
import org.catrobat.catroid.ui.dialogs.CustomAlertDialogBuilder;
import org.catrobat.catroid.utils.UtilFile;
import org.catrobat.catroid.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SelectProgramFragment extends SherlockListFragment implements OnProjectEditListener {
	private String selectedProject;
	private SelectProgramFragment selectProgramFragment;

	private List<ProjectData> projectList;
	private ProjectAdapter adapter;

	private ActionMode actionMode;
	private static String deleteActionModeTitle;
	private ProjectData projectToEdit;

	private ProjectManager projectManagerLWP = ProjectManager.getInstance(ProjectManagerState.LWP);
	private ProjectManager projectManager = ProjectManager.getInstance(ProjectManagerState.NORMAL);

	private int soundSeekBarVolume;

	private View selectAllActionModeButton;
	private ProjectListInitReceiver ListInitReceiver;
	private static final String SHARED_PREFERENCE_NAME = "showDetailsMyProjects";
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		selectProgramFragment = this;
		return inflater.inflate(R.layout.fragment_lwp_select_program, container, false);
	}



	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		initListeners();
	}


	@Override
	public void onPause() {
		super.onPause();

		if (ListInitReceiver != null) {
			getActivity().unregisterReceiver(ListInitReceiver);
		}

	}

	@Override
	public void onResume() {
		super.onResume();

		if (actionMode != null) {
			actionMode.finish();
			actionMode = null;
		}

		if (ListInitReceiver == null) {
			ListInitReceiver = new ProjectListInitReceiver();
		}

		IntentFilter intentFilterSpriteListInit = new IntentFilter(MyProjectsActivity.ACTION_PROJECT_LIST_INIT);
		getActivity().registerReceiver(ListInitReceiver, intentFilterSpriteListInit);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity()
				.getApplicationContext());

		setShowDetails(settings.getBoolean(SHARED_PREFERENCE_NAME, false));

		initAdapter();
	}



	public void setShowDetails(boolean showDetails) {
		adapter.setShowDetails(showDetails);
		adapter.notifyDataSetChanged();
	}
	public void disableTinting() {
		LiveWallpaper.getInstance().disableTinting();
	}

	private void initListeners() {
		File rootDirectory = new File(Constants.DEFAULT_ROOT);
		File projectCodeFile;
		projectList = new ArrayList<ProjectData>();
		for (String projectName : UtilFile.getProjectNames(rootDirectory)) {
			projectCodeFile = new File(Utils.buildPath(Utils.buildProjectPath(projectName), Constants.PROJECTCODE_NAME));
			projectList.add(new ProjectData(projectName, projectCodeFile.lastModified()));
		}

		Collections.sort(projectList, new SortIgnoreCase());

		adapter = new ProjectAdapter(getActivity(), R.layout.activity_my_projects_list_item,
				R.id.my_projects_activity_project_title, projectList);
		setListAdapter(adapter);
		initClickListener();
	}

	private void initClickListener() {
		adapter.setOnProjectEditListener(this);
	}

	public void startDeleteActionMode() {
		if (actionMode == null) {
			actionMode = getSherlockActivity().startActionMode(deleteModeCallBack);
			Log.d("LWP","delete Action Mode started!");
		}
	}

	private class SortIgnoreCase implements Comparator<ProjectData> {
		@Override
		public int compare(ProjectData o1, ProjectData o2) {
			String s1 = o1.projectName;
			String s2 = o2.projectName;
			return s1.toLowerCase(Locale.getDefault()).compareTo(s2.toLowerCase(Locale.getDefault()));
		}
	}

	private class LoadProject extends AsyncTask<String, String, String> {
		private ProgressDialog progress;

		public LoadProject() {
			progress = new ProgressDialog(getActivity());
			progress.setTitle(getActivity().getString(R.string.please_wait));
			progress.setMessage(getActivity().getString(R.string.loading));
			progress.setCancelable(false);
		}

		@Override
		protected void onPreExecute() {
			//LiveWallpaper.getInstance().presetSprites();
			progress.show();
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String... params) {
			//Project project = StorageHandler.getInstance().loadProject(selectedProject);
			//if (project != null) {
			//	if (projectManager.getCurrentProject() != null
			//			&& projectManager.getCurrentProject().getName().equals(selectedProject)) {
			//		getFragmentManager().beginTransaction().remove(selectProgramFragment).commit();
			//		getFragmentManager().popBackStack();
			//		return null;
			//	}
			//	projectManager.setProject(project);
			//	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
			//	Editor editor = sharedPreferences.edit();
			//	editor.putString(Constants.PREF_PROJECTNAME_KEY, selectedProject);
			//	editor.commit();
			//}
			String str_loadable = ProjectLoadableEnum.IS_ALREADY_LOADED.toString();

			synchronized (LiveWallpaper.getInstance()) {
				if (projectManagerLWP.getCurrentProject() != null
						&& projectManagerLWP.getCurrentProject().getName().equals(selectedProject)) {
					//getFragmentManager().beginTransaction().remove(selectProgramFragment).commit();
					//getFragmentManager().popBackStack();
					return str_loadable;
				}

				boolean preview_loadable = true;
				try {
					Context context = LiveWallpaper.getInstance().getContext();
					projectManagerLWP.loadProject(selectedProject, context);
				} catch (LoadingProjectException e) {
					preview_loadable = false;
					e.printStackTrace();
				} catch (OutdatedVersionProjectException e) {
					preview_loadable = false;
					e.printStackTrace();
				} catch (CompatibilityProjectException e) {
					preview_loadable = false;
					e.printStackTrace();
				}

				if (!preview_loadable) {
					getFragmentManager().beginTransaction().remove(selectProgramFragment).commit();
					getFragmentManager().popBackStack();
					str_loadable = ProjectLoadableEnum.IS_NOT_LOADABLE.toString();
					return str_loadable;
				}

				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
				Editor editor = sharedPreferences.edit();
				editor.putString(Constants.PREF_LWP_PROJECTNAME_KEY, selectedProject);
				editor.commit();
				str_loadable = ProjectLoadableEnum.IS_LOADABLE.toString();
			}

			return str_loadable;

		}

		@Override
		protected void onPostExecute(String result) {
			if (result.equals(ProjectLoadableEnum.IS_NOT_LOADABLE.toString())
					|| result.equals(ProjectLoadableEnum.IS_ALREADY_LOADED.toString())) {
				if (progress.isShowing()) {
					progress.dismiss();
				}
				Toast toast = Toast.makeText(LiveWallpaper.getInstance().getContext(), result, Toast.LENGTH_LONG);
				toast.show();

				return;
			}

			Toast toast = Toast.makeText(LiveWallpaper.getInstance().getContext(), result, Toast.LENGTH_LONG);
			toast.show();
			if (progress.isShowing()) {
				LiveWallpaper.getInstance().changeWallpaperProgram();
				progress.dismiss();
			}
			super.onPostExecute(result);
		}
	}

	public void onProjectClicked(int position) {
		selectedProject = projectList.get(position).projectName;
		CheckBox checkBox = new CheckBox(getActivity());
		checkBox.setText(R.string.lwp_enable_sound);
		SeekBar seekBar = new SeekBar(getActivity());
		seekBar.setMax(100);

		seekBar.setProgress(1);
		seekBar.setVisibility(View.VISIBLE);
		seekBar.setProgress((int)SoundManager.getInstance().getVolume());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
		seekBar.setLayoutParams(lp);

		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			public void onStopTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
			}

			public void onStartTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
			}

			public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
				// TODO Auto-generated method stub
				Log.d("SelectProgramFragment", "SeekBar Changelistener progress changed to " + String.valueOf(arg1));
				SoundManager.getInstance().setVolume(arg1);
				soundSeekBarVolume = arg1;
			}
		});



			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		checkBox.setChecked(!sharedPreferences.getBoolean(Constants.PREF_SOUND_DISABLED, false));
		SoundManager.getInstance().setVolume(50);
		checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					SoundManager.getInstance().setVolume(50);
					Log.d("LWP", "Enable Sound Volume is :" +SoundManager.getInstance().getVolume()+" CHECK!");
				} else {
					SoundManager.getInstance().setVolume(0);
					Log.d("LWP", "Enable Sound Volume is :" +SoundManager.getInstance().getVolume()+"  UNCHECK!");
				}
			}
		});

		LinearLayout linearLayout = new LinearLayout(getActivity());
		linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		linearLayout.addView(checkBox);
		linearLayout.addView(seekBar);
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setView(linearLayout);
		builder.setTitle(selectedProject);
		builder.setMessage(R.string.lwp_confirm_set_program_message);
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();

			}
		});

		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@SuppressLint("NewApi")
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LoadProject Loader = new LoadProject();
				Loader.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
			}
		});
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	private ActionMode.Callback deleteModeCallBack = new ActionMode.Callback() {
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			setSelectMode(ListView.CHOICE_MODE_MULTIPLE);

			deleteActionModeTitle = getString(R.string.delete);

			mode.setTitle(deleteActionModeTitle);
			addSelectAllActionModeButton(mode, menu);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (adapter.getAmountOfCheckedProjects() == 0) {
				clearCheckedProjectsAndEnableButtons();
			} else {
				checkIfCurrentProgramSelectedForDeletion();
			}
		}

		public void setSelectMode(int selectMode) {
			adapter.setSelectMode(selectMode);
			adapter.notifyDataSetChanged();
		}

	};

	public int getSeekbarProgress() {
		return soundSeekBarVolume;
	}

	private void addSelectAllActionModeButton(ActionMode mode, Menu menu) {
		selectAllActionModeButton = Utils.addSelectAllActionModeButton(getLayoutInflater(null), mode, menu);
		selectAllActionModeButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				for (int position = 0; position < projectList.size(); position++) {
					adapter.addCheckedProject(position);
				}
				adapter.notifyDataSetChanged();
				//onProjectChecked();
			}

		});
	}

	private void clearCheckedProjectsAndEnableButtons() {
		setSelectMode(ListView.CHOICE_MODE_NONE);
		adapter.clearCheckedProjects();

		actionMode = null;
	}

	private void checkIfCurrentProgramSelectedForDeletion() {

		boolean currentProgramSelected = false;
		Project currentProject = projectManagerLWP.getCurrentProject();
		for (int position : adapter.getCheckedProjects()) {
			ProjectData tempProjectData = (ProjectData) getListView().getItemAtPosition(position);
			if (currentProject.getName().equalsIgnoreCase(tempProjectData.projectName)) {
				currentProgramSelected = true;
				break;
			}
		}

		if (!currentProgramSelected) {
			showConfirmDeleteDialog();
			return;
		}

		AlertDialog.Builder builder = new CustomAlertDialogBuilder(getActivity());
		builder.setTitle(R.string.error);

		if (adapter.getAmountOfCheckedProjects() == 1) {
			builder.setMessage(R.string.lwp_error_delete_current_program);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					clearCheckedProjectsAndEnableButtons();
				}
			});

		} else {
			builder.setMessage(R.string.lwp_error_delete_multiple_program);
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					showConfirmDeleteDialog();
				}
			});
			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					clearCheckedProjectsAndEnableButtons();
				}
			});
		}

		AlertDialog alertDialog = builder.create();
		alertDialog.show();

	}

	public void disableEffects()
	{
		LiveWallpaper.getInstance().disableEffects();
	}

	private void showConfirmDeleteDialog() {
		int titleId;
		if (adapter.getAmountOfCheckedProjects() == 1) {
			titleId = R.string.dialog_confirm_delete_program_title;
		} else {
			titleId = R.string.dialog_confirm_delete_multiple_programs_title;
		}

		AlertDialog.Builder builder = new CustomAlertDialogBuilder(getActivity());
		builder.setTitle(titleId);
		builder.setMessage(R.string.dialog_confirm_delete_program_message);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				deleteCheckedProjects();
				clearCheckedProjectsAndEnableButtons();
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				clearCheckedProjectsAndEnableButtons();
				dialog.cancel();
			}
		});

		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	private void deleteCheckedProjects() {
		int numDeleted = 0;
		for (int position : adapter.getCheckedProjects()) {
			projectToEdit = (ProjectData) getListView().getItemAtPosition(position - numDeleted);
			if (projectToEdit.projectName.equalsIgnoreCase(projectManagerLWP.getCurrentProject().getName())) {
				continue;
			}
			deleteProject();
			numDeleted++;
		}

		if (projectList.isEmpty()) {
			projectManagerLWP.initializeDefaultProject(getActivity());
		} else if (projectManagerLWP.getCurrentProject() == null) {
			Utils.saveToPreferences(getActivity().getApplicationContext(), Constants.PREF_PROJECTNAME_KEY,
					projectList.get(0).projectName);
		}

		initAdapter();
	}

	private void deleteProject() {
		StorageHandler.getInstance().deleteProject(projectToEdit);
		projectList.remove(projectToEdit);
	}

	private void initAdapter() {
		File rootDirectory = new File(Constants.DEFAULT_ROOT);
		File projectCodeFile;
		projectList = new ArrayList<ProjectData>();
		for (String projectName : UtilFile.getProjectNames(rootDirectory)) {
			projectCodeFile = new File(Utils.buildPath(Utils.buildProjectPath(projectName), Constants.PROJECTCODE_NAME));
			projectList.add(new ProjectData(projectName, projectCodeFile.lastModified()));
		}
		Collections.sort(projectList, new SortIgnoreCase());

		adapter = new ProjectAdapter(getActivity(), R.layout.activity_my_projects_list_item,
				R.id.my_projects_activity_project_title, projectList);
		setListAdapter(adapter);
		initClickListener();
	}

	public void setSelectMode(int selectMode) {
		adapter.setSelectMode(selectMode);
		adapter.notifyDataSetChanged();
	}

	public List<ProjectData> getProjectList() {
		return projectList;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.catrobat.catroid.ui.adapter.ProjectAdapter.OnProjectClickedListener.OnProjectEditListener#onProjectEdit(int)
	 */
	@Override
	public void onProjectEdit(int position) {
		onProjectClicked(position);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.catrobat.catroid.ui.adapter.ProjectAdapter.OnProjectEditListener#onProjectChecked()
	 */
	@Override
	public void onProjectChecked() {
		// TODO Auto-generated method stub

	}

	/**
	 * @param tintingColor2
	 */
	public void tinting(int tintingColor) {
		LiveWallpaper.getInstance().tinting(tintingColor);
	}

	private class ProjectListInitReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SelectProgramActivity.ACTION_PROJECT_LIST_INIT)) {
				adapter.notifyDataSetChanged();
			}
		}
	}
}

