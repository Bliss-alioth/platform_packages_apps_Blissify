/*
 * Copyright (C) 2014-2021 The BlissRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.blissroms.blissify.fragments.qs;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.systemui.qs.QSLayoutUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.settingslib.widget.LayoutPreference;
import com.bliss.support.preferences.CustomSeekBarPreference;
import com.bliss.support.preferences.SystemSettingEditTextPreference;
import com.bliss.support.preferences.SystemSettingMasterSwitchPreference;
import com.bliss.support.preferences.SystemSettingListPreference;
import com.bliss.support.preferences.SystemSettingSwitchPreference;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private ListPreference mQuickPulldown;

    private static final String KEY_QS_HIDE_LABEL = "qs_tile_label_hide";
    private static final String KEY_QS_VERTICAL_LAYOUT = "qs_tile_vertical_layout";
    private static final String KEY_QS_COLUMN_PORTRAIT = "qs_layout_columns";
    private static final String KEY_QS_ROW_PORTRAIT = "qs_layout_rows";
    private static final String KEY_QQS_ROW_PORTRAIT = "qqs_layout_rows";
    private static final String KEY_APPLY_CHANGE_BUTTON = "apply_change_button";

    private Context mContext;

    private CustomSeekBarPreference mQsColumns;
    private CustomSeekBarPreference mQsRows;
    private CustomSeekBarPreference mQqsRows;

    private Button mApplyChange;

    private SystemSettingSwitchPreference mHide;
    private SystemSettingSwitchPreference mVertical;

    private int[] currentValue = new int[2];

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.blissify_quicksettings);

        int qpmode = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0, UserHandle.USER_CURRENT);
        mQuickPulldown = (ListPreference) findPreference("qs_quick_pulldown");
        mQuickPulldown.setValue(String.valueOf(qpmode));
        mQuickPulldown.setSummary(mQuickPulldown.getEntry());
        mQuickPulldown.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mQsColumns = (CustomSeekBarPreference) findPreference(KEY_QS_COLUMN_PORTRAIT);
        mQsColumns.setOnPreferenceChangeListener(this);

        mQsRows = (CustomSeekBarPreference) findPreference(KEY_QS_ROW_PORTRAIT);
        mQsRows.setOnPreferenceChangeListener(this);

        mQqsRows = (CustomSeekBarPreference) findPreference(KEY_QQS_ROW_PORTRAIT);
        mQqsRows.setOnPreferenceChangeListener(this);

        mContext = getContext();

        LayoutPreference preference = findPreference(KEY_APPLY_CHANGE_BUTTON);
        mApplyChange = (Button) preference.findViewById(R.id.apply_change);
        mApplyChange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mApplyChange.isEnabled()) {
                    final int[] newValue = {
                        mQsRows.getValue() * 10 + mQsColumns.getValue(),
                        mQqsRows.getValue() * 10 + mQsColumns.getValue()
                    };
                    Settings.System.putIntForUser(getContentResolver(),
                            Settings.System.QS_LAYOUT, newValue[0], UserHandle.USER_CURRENT);
                    Settings.System.putIntForUser(getContentResolver(),
                            Settings.System.QQS_LAYOUT, newValue[1], UserHandle.USER_CURRENT);
                    if (QSLayoutUtils.updateLayout(mContext)) {
                        currentValue[0] = newValue[0];
                        currentValue[1] = newValue[1];
                        mApplyChange.setEnabled(false);
                    } else {
                        Settings.System.putIntForUser(getContentResolver(),
                                Settings.System.QS_LAYOUT, currentValue[0], UserHandle.USER_CURRENT);
                        Settings.System.putIntForUser(getContentResolver(),
                                Settings.System.QQS_LAYOUT, currentValue[1], UserHandle.USER_CURRENT);
                        Toast.makeText(mContext, R.string.qs_apply_change_failed, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        initPreference();

        final boolean hideLabel = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.QS_TILE_LABEL_HIDE, 0, UserHandle.USER_CURRENT) == 1;

        mHide = (SystemSettingSwitchPreference) findPreference(KEY_QS_HIDE_LABEL);
        mHide.setOnPreferenceChangeListener(this);

        mVertical = (SystemSettingSwitchPreference) findPreference(KEY_QS_VERTICAL_LAYOUT);
        mVertical.setEnabled(!hideLabel);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN, value,
                    UserHandle.USER_CURRENT);
            int index = mQuickPulldown.findIndexOfValue((String) newValue);
            mQuickPulldown.setSummary(
                    mQuickPulldown.getEntries()[index]);
            return true;
        }
        if (preference == mHide) {
            boolean hideLabel = (Boolean) newValue;
            mVertical.setEnabled(!hideLabel);
        } else if (preference == mQsColumns) {
            int qs_columns = Integer.parseInt(newValue.toString());
            mApplyChange.setEnabled(
                currentValue[0] != mQsRows.getValue() * 10 + qs_columns ||
                currentValue[1] != mQqsRows.getValue() * 10 + qs_columns
            );
        } else if (preference == mQsRows) {
            int qs_rows = Integer.parseInt(newValue.toString());
            mQqsRows.setMax(qs_rows - 1);
            if (mQqsRows.getValue() > qs_rows - 1) {
                mQqsRows.setValue(qs_rows - 1);
            }
            mApplyChange.setEnabled(
                currentValue[0] != qs_rows * 10 + mQsColumns.getValue() ||
                currentValue[1] != mQqsRows.getValue() * 10 + mQsColumns.getValue()
            );
        } else if (preference == mQqsRows) {
            int qqs_rows = Integer.parseInt(newValue.toString());
            mApplyChange.setEnabled(
                currentValue[0] != mQsRows.getValue() * 10 + mQsColumns.getValue() ||
                currentValue[1] != qqs_rows * 10 + mQsColumns.getValue()
            );
        }
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    private void initPreference() {
        final int index_qs = Settings.System.getIntForUser(getContentResolver(),
            Settings.System.QS_LAYOUT, 42, UserHandle.USER_CURRENT);
        final int index_qqs = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.QQS_LAYOUT, 22, UserHandle.USER_CURRENT);
        mQsColumns.setValue(index_qs % 10);
        mQsRows.setValue(index_qs / 10);
        mQqsRows.setValue(index_qqs / 10);
        mQqsRows.setMax(mQsRows.getValue() - 1);
        currentValue[0] = index_qs;
        currentValue[1] = index_qqs;
    }

    /**
     * For Search.
     */

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.blissify_quicksettings);
}
