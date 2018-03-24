/*
Copyright 2018 Daniel Monedero-Tortola

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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;


public class AddUpdateProductActivity extends Activity {
	private String addOrUpdate = "add";
	private Integer productId = null;
	private WeightUnit weightUnit = null;

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_update_product);

		// intent extras
		Bundle extras = getIntent().getExtras();

		String weightUnitString = extras.getString("weightUnit");
		weightUnit = WeightUnit.fromName(weightUnitString);
		TextView pricePerWeightEntry = (TextView) findViewById(R.id.price_per_weight_entry);
		pricePerWeightEntry.setHint(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram_hint : R.string.price_per_pound_hint);

		String pricePerWeightString = extras.getString("pricePerWeight");
		if (pricePerWeightString != null) {
			pricePerWeightEntry.setText(pricePerWeightString);
		}

		productId = extras.getInt("productId");
		if (productId != null) {
			final dbHelper helper = new dbHelper(this);
			Cursor results = helper.getProduct(productId);
			results.moveToFirst();
			if (results.isAfterLast() == false) {
				((EditText) findViewById(R.id.product_entry)).setText(results.getString(1));
				((EditText) findViewById(R.id.price_per_weight_entry)).setText(results.getString(2));
				((AutoCompleteTextView) findViewById(R.id.place_entry_autocomplete)).setText(results.getString(3));
				((EditText) findViewById(R.id.url_entry)).setText(results.getString(4));
			}
			results.close();
			helper.close();
		}

		addOrUpdate = extras.getString("addOrUpdate");
		final Button deleteButton = (Button) findViewById(R.id.delete_button);
		if (addOrUpdate.equals("add")) {
			deleteButton.setEnabled(false);
		}

		// listeners
		// listen for the done key
		EditText pricePerWeight = (EditText) findViewById(R.id.price_per_weight_entry);
		pricePerWeight.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_DONE || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
							addOrUpdateProduct();
							return true;
						}
						return false;
					}
				}
		);

		EditText urlEntry = (EditText) findViewById(R.id.url_entry);
		urlEntry.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_DONE || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
							addOrUpdateProduct();
							return true;
						}
						return false;
					}
				}
		);

		// listen for the done button
		final Button doneButton = (Button) findViewById(R.id.done_button);
		doneButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				addOrUpdateProduct();
			}
		});

		// listen for the delete button
		deleteButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				confirmDelete();
			}
		});

		// text change listeners
		final AutoCompleteTextView place = ((AutoCompleteTextView) findViewById(R.id.place_entry_autocomplete));
		final EditText productName = ((EditText) findViewById(R.id.product_entry));
		final EditText pricePerWeightStr = ((EditText) findViewById(R.id.price_per_weight_entry));

		TextWatcher textWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (((place.getText().toString() == null) || (place.getText().toString().equals(""))) ||
					((productName.getText().toString() == null) || (productName.getText().toString().equals(""))) ||
				((pricePerWeightStr.getText().toString() == null) || (pricePerWeightStr.getText().toString().equals("")))) {
					doneButton.setEnabled(false);
				} else {
					doneButton.setEnabled(true);
				}
			}
		};
		// initialize the enable state
		textWatcher.onTextChanged("", 0, 0, 0);

		place.addTextChangedListener(textWatcher);
		productName.addTextChangedListener(textWatcher);
		pricePerWeightStr.addTextChangedListener(textWatcher);

		final Button historyButton = (Button) findViewById(R.id.history_button);
		if (addOrUpdate.equals("add")) {
			historyButton.setVisibility(View.GONE);
		} else {
			int dataPointCounter = 0;
			String productNameString = null;
			float currentProductPricePerWeight = 0.0f;
			boolean showHistory = false;

			final dbHelper helper = new dbHelper(this);

			Cursor results = helper.getProduct(productId);
			results.moveToFirst();
			if (results.isAfterLast() == false) {
				productNameString = results.getString(1);
				currentProductPricePerWeight = results.getFloat(2);
			}
			results.close();

			results = helper.getProductRevisionPricesPerWeight(productNameString);
			results.moveToFirst();
			if (results.isAfterLast() == false) {
				showHistory = true;
			}
			results.close();
			helper.close();

			if (!showHistory) {
				historyButton.setVisibility(View.GONE);
			}
		}

		// listen for the history button
		historyButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(AddUpdateProductActivity.this, HistoryActivity.class);
				intent.putExtra("weightUnit", weightUnit);
				intent.putExtra("productId", productId);
				startActivity(intent);
			}
		});

		// place autocomplete
		ArrayList<String> places = new ArrayList<String>();
		final dbHelper helper = new dbHelper(this);
		Cursor results = helper.getPlaces();

		results.moveToFirst();
		while (results.isAfterLast() == false) {
			places.add(results.getString(0));
			results.moveToNext();
		}
		results.close();
		helper.close();

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.simple_drop_down, places);
		place.setAdapter(adapter);
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			Intent intent = new Intent(AddUpdateProductActivity.this, SettingsActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void addOrUpdateProduct() {
		// add it to the db
		String place = "";
		String productName = "";
		float pricePerWeightStr = 0;
		String url = "";
		final dbHelper helper = new dbHelper(this);
		place = ((EditText) findViewById(R.id.place_entry_autocomplete)).getText().toString();
		productName = ((EditText) findViewById(R.id.product_entry)).getText().toString();
		pricePerWeightStr = Float.valueOf(((EditText) findViewById(R.id.price_per_weight_entry)).getText().toString());
		url = ((EditText) findViewById(R.id.url_entry)).getText().toString();

		if (addOrUpdate.equals("add")) {
			productId = helper.addProduct(productName, pricePerWeightStr, place, url);
		} else {
			productId = helper.updateProduct(productId, productName, pricePerWeightStr, place, url);
		}
		helper.close();

		// go back
		Intent intent = new Intent();
		intent.putExtra("productId", productId);
		intent.putExtra("productName", productName);
		setResult(Activity.RESULT_OK, intent);
		finish();
	}

	private void confirmDelete()	{
		AlertDialog myQuittingDialogBox =new AlertDialog.Builder(this)
				.setTitle(R.string.delete)
				.setMessage(R.string.delete_confirmation)
				.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						final dbHelper helper = new dbHelper(AddUpdateProductActivity.this);
						helper.deleteProductButBackup(productId);
						helper.close();

						dialog.dismiss();

						// go back
						Intent intent = new Intent();
						intent.putExtra("productId", -1);
						setResult(Activity.RESULT_OK, intent);
						finish();
					}

				})
				.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				})
				.create();
		myQuittingDialogBox.show();
	}
}