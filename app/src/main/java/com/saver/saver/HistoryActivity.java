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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class HistoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.product_history);

        // intent extras
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return;
        }
        WeightUnit weightUnit = (WeightUnit) extras.get("weightUnit");
        if (weightUnit != null) {
            TextView pricePerWeightHeader = findViewById(R.id.price_per_weight_header);
            pricePerWeightHeader.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram_hint : R.string.price_per_pound_hint);
        }

        int productId = extras.getInt("productId");
        String productName = null;
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
        @SuppressLint("SimpleDateFormat") SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final dbHelper helper = new dbHelper(this);

        // current one
        Cursor results = helper.getProduct(productId);
        results.moveToFirst();
        if (!results.isAfterLast()) {
            productName = results.getString(1);
            String pricePerWeight = results.getString(2);
            String place = results.getString(3);
            String date = "";

            try {
                date = dateFormat.format(Objects.requireNonNull(iso8601Format.parse(results.getString(5))));
            } catch (ParseException e) {
                Log.e("", "Parsing ISO8601 datetime failed", e);
            }

            TextView productNameTitle = findViewById(R.id.product_name_title);
            productNameTitle.setText(productName);
            addRow(pricePerWeight, place, date);
        }
        results.close();

        // all the other
        results = helper.getProductHistory(productName);
        results.moveToFirst();
        while (!results.isAfterLast()) {
            String pricePerWeight = results.getString(0);
            String place = results.getString(1);
            String date = "";

            try {
                date = dateFormat.format(Objects.requireNonNull(iso8601Format.parse(results.getString(2))));
            } catch (ParseException e) {
                Log.e("", "Parsing ISO8601 datetime failed", e);
            }


            addRow(pricePerWeight, place, date);
            results.moveToNext();
        }

        results.close();
        helper.close();
    }

    private void addRow(String pricePerWeight, String place, String date) {
        TableLayout ll = findViewById(R.id.history_table);

        TableRow row= new TableRow(this);
        TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(layoutParams);

        TextView pricePerWeightTextView = new TextView(this);
        pricePerWeightTextView.setText(pricePerWeight);
        row.addView(pricePerWeightTextView);

        TextView placeTextView = new TextView(this);
        placeTextView.setText(place);
        row.addView(placeTextView);

        TextView dateTextView = new TextView(this);
        dateTextView.setText(date);
        row.addView(dateTextView);

        ll.addView(row);
    }
}
