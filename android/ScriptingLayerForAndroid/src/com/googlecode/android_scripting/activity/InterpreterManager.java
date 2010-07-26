/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.activity;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.googlecode.android_scripting.ActivityFlinger;
import com.googlecode.android_scripting.Analytics;
import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.FeaturedInterpreters;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.dialog.Help;
import com.googlecode.android_scripting.interpreter.Interpreter;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration.ConfigurationObserver;

public class InterpreterManager extends ListActivity {

  private InterpreterManagerAdapter mAdapter;
  private InterpreterListObserver mObserver;
  private List<Interpreter> mInterpreterList;
  private List<String> mFeaturedInterpreters;
  private InterpreterConfiguration mConfiguration;

  private static enum MenuId {
    HELP, ADD, NETWORK, PREFERENCES;
    public int getId() {
      return ordinal() + Menu.FIRST;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    CustomizeWindow.requestCustomTitle(this, "Interpreters", R.layout.interpreter_manager);
    mConfiguration = ((BaseApplication) getApplication()).getInterpreterConfiguration();
    mInterpreterList = new ArrayList<Interpreter>();
    mAdapter = new InterpreterManagerAdapter();
    mObserver = new InterpreterListObserver();
    mAdapter.registerDataSetObserver(mObserver);
    setListAdapter(mAdapter);
    registerForContextMenu(getListView());
    ActivityFlinger.attachView(getListView(), this);
    ActivityFlinger.attachView(getWindow().getDecorView(), this);
    mFeaturedInterpreters = FeaturedInterpreters.getList();
    Analytics.trackActivity(this);
  }

  @Override
  public void onStart() {
    super.onStart();
    mConfiguration.registerObserver(mObserver);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mAdapter.notifyDataSetInvalidated();
  }

  @Override
  public void onStop() {
    super.onStop();
    mConfiguration.unregisterObserver(mObserver);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    buildInstallLanguagesMenu(menu);
    menu.add(Menu.NONE, MenuId.NETWORK.getId(), Menu.NONE, "Start Server").setIcon(
        android.R.drawable.ic_menu_share);
    menu.add(Menu.NONE, MenuId.PREFERENCES.getId(), Menu.NONE, "Preferences").setIcon(
        android.R.drawable.ic_menu_preferences);
    menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
        android.R.drawable.ic_menu_help);
    return super.onPrepareOptionsMenu(menu);
  }

  private void buildInstallLanguagesMenu(Menu menu) {
    SubMenu installMenu =
        menu.addSubMenu(Menu.NONE, MenuId.ADD.getId(), Menu.NONE, "Add").setIcon(
            android.R.drawable.ic_menu_add);
    int i = MenuId.values().length + Menu.FIRST;
    for (String interpreterName : mFeaturedInterpreters) {
      installMenu.add(Menu.NONE, i++, Menu.NONE, interpreterName);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == MenuId.HELP.getId()) {
      Help.show(this);
    } else if (itemId == MenuId.NETWORK.getId()) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(this);
      dialog.setItems(new CharSequence[] { "Public", "Private" }, new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          launchService(which == 0 /* usePublicIp */);
        }
      });
      dialog.show();
    } else if (itemId == MenuId.PREFERENCES.getId()) {
      startActivity(new Intent(this, Preferences.class));
    } else if (itemId > MenuId.values().length + Menu.FIRST) {
      int i = itemId - MenuId.values().length - Menu.FIRST;
      if (i < mFeaturedInterpreters.size()) {
        URL url = FeaturedInterpreters.getUrlForName(mFeaturedInterpreters.get(i));
        Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse(url.toString()));
        startActivity(viewIntent);
      }
    }
    return true;
  }

  private void launchService(boolean usePublicIp) {
    Intent intent = new Intent(this, ScriptingLayerService.class);
    intent.setAction(Constants.ACTION_LAUNCH_SERVER);
    intent.putExtra(Constants.EXTRA_USE_EXTERNAL_IP, usePublicIp);
    startService(intent);
  }

  private void launchTerminal(Interpreter interpreter) {
    Intent intent = new Intent(this, ScriptingLayerService.class);
    intent.setAction(Constants.ACTION_LAUNCH_INTERPRETER);
    intent.putExtra(Constants.EXTRA_INTERPRETER_NAME, interpreter.getName());
    startService(intent);
  }

  @Override
  protected void onListItemClick(ListView list, View view, int position, long id) {
    Interpreter interpreter = (Interpreter) list.getItemAtPosition(position);
    launchTerminal(interpreter);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mConfiguration.unregisterObserver(mObserver);
  }

  private class InterpreterListObserver extends DataSetObserver implements ConfigurationObserver {
    @Override
    public void onInvalidated() {
      mInterpreterList = mConfiguration.getInstalledInterpreters();
    }

    @Override
    public void onChanged() {
      mInterpreterList = mConfiguration.getInstalledInterpreters();
    }

    @Override
    public void onConfigurationChanged() {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          mAdapter.notifyDataSetChanged();
        }
      });
    }
  }

  private class InterpreterManagerAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return mInterpreterList.size();
    }

    @Override
    public Object getItem(int position) {
      return mInterpreterList.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      TextView view = new TextView(InterpreterManager.this);
      view.setPadding(2, 2, 2, 2);
      view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
      view.setText(mInterpreterList.get(position).getNiceName());
      return view;
    }
  }
}