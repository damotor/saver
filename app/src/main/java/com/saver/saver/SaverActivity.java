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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SaverActivity extends Activity {
	public static final int ADD_PRODUCT_VALUE = 0;
	public static final int UPDATE_PRODUCT_VALUE = 1;
	private Integer productId = null;
	private WeightUnit weightUnit = null;
	private boolean isPreviousActivitySettings = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// load preferences
		loadPreferences();

		// listen for the done key
		final EditText weight = (EditText) findViewById(R.id.weight_entry);
		weight.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {

					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_DONE || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
							calculatePricePerWeight();
							return true;
						}
						return false;
					}
				});
		// clean weight on long press
		weight.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) {
				weight.setText("");
				return true;
			}
		});

		// clean price on long press
		final EditText price = (EditText) findViewById(R.id.price_entry);
		price.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) {
				price.setText("");
				return true;
			}
		});

		// focus on price and show keyboard at the beginning
		price.postDelayed(new Runnable() {
			public void run() {
				InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				keyboard.showSoftInput(price, 0);
			}
		}, 200);

		// listen for the calculate button
		final Button calculateButton = (Button) findViewById(R.id.calculate_button);
		calculateButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				calculatePricePerWeight();
			}
		});

		// product names
		final AutoCompleteTextView textViewProductsNames = refreshProducts();
		textViewProductsNames.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				productId = ((Product) arg0.getItemAtPosition(arg2)).getId();
				setProductInfoLabel(productId);
			}
		});

		// listen for the clear all button
		final Button clearButton = (Button) findViewById(R.id.clear_all_button);
		clearButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				clearFields();
			}
		});

		// listen for the add product button
		final Button newProductButton = (Button) findViewById(R.id.new_product_button);
		newProductButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(SaverActivity.this, AddUpdateProductActivity.class);
				TextView pricePerWeight = (TextView) findViewById(R.id.price_per_weight_label);
				String pricePerWeightString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
				intent.putExtra("pricePerWeight", pricePerWeight.getText().toString().replace(pricePerWeightString, ""));
				intent.putExtra("weightUnit", weightUnit.toString());
				intent.putExtra("addOrUpdate", "add");
				startActivityForResult(intent, ADD_PRODUCT_VALUE);
			}
		});

		// listen for the update product button
		final Button updateProductButton = (Button) findViewById(R.id.update_product_button);
		updateProductButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				AutoCompleteTextView textViewProducts = (AutoCompleteTextView) findViewById(R.id.autocomplete_product);
				Intent intent = new Intent(SaverActivity.this, AddUpdateProductActivity.class);
				intent.putExtra("productId", productId);
				intent.putExtra("productName", textViewProducts.getText().toString());
				intent.putExtra("weightUnit", weightUnit.toString());
				intent.putExtra("addOrUpdate", "update");
				startActivityForResult(intent, UPDATE_PRODUCT_VALUE);
			}
		});

		// text change listeners
		// for the calculate button
		TextWatcher priceWeightTextWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (((price.getText().toString() == null) || (price.getText().toString().equals(""))) ||
						((weight.getText().toString() == null) || (weight.getText().toString().equals("")))) {
					calculateButton.setEnabled(false);
				} else {
					calculateButton.setEnabled(true);
				}
			}
		};
		// initialize the enable state
		priceWeightTextWatcher.onTextChanged("", 0, 0, 0);

		price.addTextChangedListener(priceWeightTextWatcher);
		weight.addTextChangedListener(priceWeightTextWatcher);

		// for the update product button
		final TextView productInformationLabel = (TextView) findViewById(R.id.product_information_label);
		final TextWatcher pricePerWeightTextWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String pricePerWeightString = productInformationLabel.getText().toString();
				String pricePerKilogram = getResources().getString(R.string.price_per_kilogram);
				String pricePerPound = getResources().getString( R.string.price_per_pound);

				if (pricePerWeightString.equals(pricePerKilogram) || pricePerWeightString.equals(pricePerPound)) {
					updateProductButton.setEnabled(false);
				} else {
					updateProductButton.setEnabled(true);
				}
			}
		};
		// initialize the enable state
		pricePerWeightTextWatcher.onTextChanged("", 0, 0, 0);
		productInformationLabel.addTextChangedListener(pricePerWeightTextWatcher);

		// clean product name on long press
		textViewProductsNames.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) {
				textViewProductsNames.setText("");
				productInformationLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);

				return true;
			}
		});

		// make sure we can click links
		productInformationLabel.setMovementMethod(LinkMovementMethod.getInstance());
	}


	// listen for finishing sub-activities
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == ADD_PRODUCT_VALUE) {
				final EditText price = (EditText) findViewById(R.id.price_entry);
				price.setText("");
				final EditText weight = (EditText) findViewById(R.id.weight_entry);
				weight.setText("");
				TextView pricePerWeightLabel = (TextView) findViewById(R.id.price_per_weight_label);
				pricePerWeightLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
			}
			// set Product Info Label
			Bundle extras = intent.getExtras();
			productId = extras.getInt("productId");
			if ((productId != null) && (productId > 0)) {
				setProductInfoLabel(productId);
			} else {
				// back from delete
				productId = null;
				AutoCompleteTextView textViewProducts = (AutoCompleteTextView) findViewById(R.id.autocomplete_product);
				textViewProducts.setText("");
				final TextView productInformationLabel = (TextView) findViewById(R.id.product_information_label);
				productInformationLabel.setText("");
			}
			// refresh products
			AutoCompleteTextView textViewProductsNames = refreshProducts();
			String productName = extras.getString("productName");
			if (productName != null) {
				textViewProductsNames.setText(productName);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		if (isPreviousActivitySettings) {
			loadPreferences();
		}
		isPreviousActivitySettings = false;
	}

	// show the settings activity
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			isPreviousActivitySettings = true;
			Intent intent = new Intent(SaverActivity.this, SettingsActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void loadPreferences() {
		String weightUnitString = PreferenceManager.getDefaultSharedPreferences(this).getString("weightUnit", null);
		if (weightUnitString != null) {
			// already saved weightUnit preference
			if (weightUnitString.equals(WeightUnit.KILOGRAMS.toString())) {
				weightUnit = WeightUnit.KILOGRAMS;
			} else {
				weightUnit = WeightUnit.POUNDS;
			}
		} else {
			weightUnit = WeightUnit.KILOGRAMS;
			String countryCode = Locale.getDefault().getCountry();
			// USA, Liberia, Burma
			if (countryCode.equals("US") || countryCode.equals("LR") || countryCode.equals("MM")) {
				weightUnit = WeightUnit.POUNDS;
			}
			// saved weightUnit preference
			SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putString("weightUnit", weightUnit.toString());
			editor.commit();
		}

		clearFields();
	}

	private void calculatePricePerWeight() {
		float price;
		float weight;
		try {
			price = Float.valueOf(((EditText) findViewById(R.id.price_entry)).getText().toString());
			weight = Float.valueOf(((EditText) findViewById(R.id.weight_entry)).getText().toString());
		}catch(Exception e){
			price = 0;
			weight = 0;
		}
		TextView pricePerWeight = (TextView) findViewById(R.id.price_per_weight_label);
		DecimalFormat df = new DecimalFormat("####.##");
		if (weightUnit == WeightUnit.POUNDS) {
			Float result = (price / weight) * 16;
			if (Float.isNaN(result)) {
				result = 0f;
			}
			pricePerWeight.setText(getResources().getString(R.string.price_per_pound) + df.format(result).replace(",", "."));
		} else {
			Float result = (price / weight) * 1000;
			if (Float.isNaN(result)) {
				result = 0f;
			}
			pricePerWeight.setText(getResources().getString(R.string.price_per_kilogram) + df.format(result).replace(",", "."));
		}
	}

	private void setProductInfoLabel(int productId) {
		final TextView productInformationLabel = (TextView) findViewById(R.id.product_information_label);
		final dbHelper helper = new dbHelper(this);
		Cursor results = helper.getProduct(productId);
		results.moveToFirst();
		if (results.isAfterLast() == false) {
			String pricePerString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
			String pricePer = results.getString(2);
			String place = results.getString(3);
			String url = results.getString(4);
			if ((url != null) && (!url.equals(""))) {
				place = "<a href=\""+url+"\">"+place+"</a>";
			}
			productInformationLabel.setText(Html.fromHtml(pricePerString + pricePer + " @ " + place));
		}
		results.close();
		helper.close();
	}

	private AutoCompleteTextView refreshProducts() {
		ArrayList<Product> products = new ArrayList<Product>();
		final dbHelper helper = new dbHelper(this);
		Cursor results = helper.getProductNames();

		results.moveToFirst();
		while (results.isAfterLast() == false) {
			products.add(Product.fromCursor(results));
			results.moveToNext();
		}
		results.close();
		helper.close();

		ArrayAdapter<Product> adapter = new ArrayAdapter<Product>(this, R.layout.simple_drop_down, products);
		final AutoCompleteTextView textViewProductsNames = (AutoCompleteTextView) findViewById(R.id.autocomplete_product);
		textViewProductsNames.setAdapter(adapter);

		return textViewProductsNames;
	}

	private void clearFields() {
		final EditText price = (EditText) findViewById(R.id.price_entry);
		price.setText("");
		final EditText weight = (EditText) findViewById(R.id.weight_entry);
		weight.setText("");
		String weightString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.grams : R.string.ounces);
		weight.setHint(weightString);
		final AutoCompleteTextView textViewProductsNames = (AutoCompleteTextView) findViewById(R.id.autocomplete_product);
		textViewProductsNames.setText("");
		final TextView productInformationLabel = (TextView) findViewById(R.id.product_information_label);
		productInformationLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
		TextView pricePerWeightLabel = (TextView) findViewById(R.id.price_per_weight_label);
		pricePerWeightLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
	}
}
