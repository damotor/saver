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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        // listen for kilograms radio button
        final Button kilogramsButton = findViewById(R.id.kilograms_radio);
        kilogramsButton.setOnClickListener(v -> saveWeightUnitPreference(WeightUnit.KILOGRAMS));

        // listen for pounds radio button
        final Button poundsButton = findViewById(R.id.pounds_radio);
        poundsButton.setOnClickListener(v -> saveWeightUnitPreference(WeightUnit.POUNDS));

        // enable/disable radios according to the preferences
        SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        String weightUnit = sharedPreferences.getString("weightUnit", null);
        if (weightUnit != null) {
            final RadioGroup weightUnitGroup = findViewById(R.id.weight_unit_group);
            if (weightUnit.equals(WeightUnit.KILOGRAMS.toString())) {
                weightUnitGroup.check(R.id.kilograms_radio);
            } else {
                weightUnitGroup.check(R.id.pounds_radio);
            }
        }

        // set database path
        final dbHelper helper = new dbHelper(this);
        TextView pricePerWeight = findViewById(R.id.database_path);
        pricePerWeight.setText(helper.getDatabasePath());
        helper.close();
    }

    private void saveWeightUnitPreference(WeightUnit weightUnit) {
        SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("weightUnit", weightUnit.toString());
        editor.apply();
    }
}
