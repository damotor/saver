/*
Copyright 2025 Daniel Monedero-Tortola

This file is part of Saver.

Saver is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Saver is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Saver.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.saver.saver;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

		final RadioGroup weightUnitGroup = findViewById(R.id.weight_unit_group);

		SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
		String savedWeightUnit = sharedPreferences.getString("weightUnit", null);
		if (savedWeightUnit != null) {
			if (savedWeightUnit.equals(WeightUnit.KILOGRAMS.toString())) {
				weightUnitGroup.check(R.id.kilograms_radio);
			} else if (savedWeightUnit.equals(WeightUnit.POUNDS.toString())) {
				weightUnitGroup.check(R.id.pounds_radio);
			}
		}

		weightUnitGroup.setOnCheckedChangeListener((group, checkedId) -> {
			if (checkedId == R.id.kilograms_radio) {
				saveWeightUnitPreference(WeightUnit.KILOGRAMS);
			} else if (checkedId == R.id.pounds_radio) {
				saveWeightUnitPreference(WeightUnit.POUNDS);
			}
		});

		try (DbHelper helper = new DbHelper(this)) {
			TextView databasePathView = findViewById(R.id.database_path);
			databasePathView.setText(helper.getDatabasePath());
		}
	}

	private void saveWeightUnitPreference(WeightUnit weightUnit) {
		SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("weightUnit", weightUnit.toString());
		editor.apply();
	}
}
