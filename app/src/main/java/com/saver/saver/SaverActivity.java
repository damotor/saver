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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

public class SaverActivity extends AppCompatActivity {
	private Integer productId = null;
	private WeightUnit weightUnit = null;
	private boolean isPreviousActivitySettings = false;
	private DbHelper db;

	private final ActivityResultLauncher<Intent> addProductLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK) {
					final EditText price = findViewById(R.id.price_entry);
					price.setText("");
					final EditText weight = findViewById(R.id.weight_entry);
					weight.setText("");
					TextView pricePerWeightLabel = findViewById(R.id.price_per_weight_label);
					pricePerWeightLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);

					Intent intent = result.getData();
					if (intent != null) {
						Bundle extras = intent.getExtras();
						if (extras != null) {
							productId = extras.getInt("productId");
							if (productId > 0) {
								setProductInfoLabel(productId);
							}
							// refresh products
							AutoCompleteTextView textViewProductsNames = refreshProducts();
							String productName = extras.getString("productName");
							if (productName != null) {
								textViewProductsNames.setText(productName);
							}
						}
					}
				}
			});

	private final ActivityResultLauncher<Intent> updateProductLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK) {
					Intent intent = result.getData();
					if (intent != null) {
						Bundle extras = intent.getExtras();
						if (extras != null) {
							productId = extras.getInt("productId");
							if (productId > 0) {
								setProductInfoLabel(productId);
							} else {
								// back from delete
								productId = null;
								AutoCompleteTextView textViewProducts = findViewById(R.id.autocomplete_product);
								textViewProducts.setText("");
								final TextView productInformationLabel = findViewById(R.id.product_information_label);
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
				}
			});

	private final androidx.activity.result.ActivityResultLauncher<Intent> importDbLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					Uri uri = result.getData().getData();
					if (uri != null) {
						boolean success = db.importDatabase(this, uri);
						if (success) {
							Toast.makeText(this, "Database imported successfully! Please restart the app.", Toast.LENGTH_LONG).show();
							// You might want to force a restart or reload data here
						} else {
							Toast.makeText(this, "Failed to import database.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			});

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		db = new DbHelper(this);

		// load preferences
		loadPreferences();

		// listen for the done key
		final EditText weight = findViewById(R.id.weight_entry);
		weight.setOnEditorActionListener(
				(v, actionId, event) -> {
					if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
						calculatePricePerWeight();
						return true;
					}
					return false;
				});
		// clean weight on long press
		weight.setOnLongClickListener(v -> {
			weight.setText("");
			return true;
		});

		// clean price on long press
		final EditText price = findViewById(R.id.price_entry);
		price.setOnLongClickListener(v -> {
			price.setText("");
			return true;
		});

		// focus on price and show keyboard at the beginning
		price.postDelayed(() -> {
			InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (keyboard != null) {
				keyboard.showSoftInput(price, 0);
			}
		}, 200);

		// listen for the calculate button
		final Button calculateButton = findViewById(R.id.calculate_button);
		calculateButton.setOnClickListener(v -> calculatePricePerWeight());

		// product names
		final AutoCompleteTextView textViewProductsNames = refreshProducts();
		textViewProductsNames.setOnItemClickListener((arg0, arg1, arg2, arg3) -> {
			productId = ((Product) arg0.getItemAtPosition(arg2)).getId();
			setProductInfoLabel(productId);
		});

		// Barcode Search logic
		TextInputLayout productSearchLayout = findViewById(R.id.autocomplete_product_layout);
		productSearchLayout.setEndIconOnClickListener(v -> {
			GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
					.enableAutoZoom()
					.build();
			GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
			scanner.startScan()
					.addOnSuccessListener(barcode -> {
						String rawValue = barcode.getRawValue();
						if (rawValue != null) {
							searchProductByBarcode(rawValue);
						}
					})
					.addOnFailureListener(e -> Toast.makeText(this, "Scan failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
		});

		// listen for the clear all button
		final Button clearButton = findViewById(R.id.clear_all_button);
		clearButton.setOnClickListener(v -> clearFields());

		// listen for the add product button
		final Button newProductButton = findViewById(R.id.new_product_button);
		newProductButton.setOnClickListener(v -> {
			Intent intent = new Intent(SaverActivity.this, AddUpdateProductActivity.class);
			TextView pricePerWeight = findViewById(R.id.price_per_weight_label);
			String pricePerWeightString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
			intent.putExtra("pricePerWeight", pricePerWeight.getText().toString().replace(pricePerWeightString, ""));
			intent.putExtra("weightUnit", weightUnit.toString());
			intent.putExtra("addOrUpdate", "add");
			addProductLauncher.launch(intent);
		});

		// listen for the update product button
		final Button updateProductButton = findViewById(R.id.update_product_button);
		updateProductButton.setOnClickListener(v -> {
			AutoCompleteTextView textViewProducts = findViewById(R.id.autocomplete_product);
			Intent intent = new Intent(SaverActivity.this, AddUpdateProductActivity.class);
			intent.putExtra("productId", productId);
			intent.putExtra("productName", textViewProducts.getText().toString());
			intent.putExtra("weightUnit", weightUnit.toString());
			intent.putExtra("addOrUpdate", "update");
			updateProductLauncher.launch(intent);
		});

		final Button exportButton = findViewById(R.id.btnExportDb);
		exportButton.setOnClickListener(v -> {
			boolean success = db.exportDatabase(SaverActivity.this);
			if (success) {
				Toast.makeText(SaverActivity.this, "Database exported to Downloads folder.", Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(SaverActivity.this, "Failed to export database.", Toast.LENGTH_SHORT).show();
			}
		});

		final Button importButton = findViewById(R.id.btnImportDb);
		importButton.setOnClickListener(v -> {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("application/octet-stream");
			importDbLauncher.launch(intent);
		});

		// text change listeners
		TextWatcher priceWeightTextWatcher = getTextWatcher(calculateButton, price, weight);
		price.addTextChangedListener(priceWeightTextWatcher);
		weight.addTextChangedListener(priceWeightTextWatcher);

		final TextView productInformationLabel = findViewById(R.id.product_information_label);
		final TextWatcher pricePerWeightTextWatcher = getTextWatcher(productInformationLabel, updateProductButton);
		productInformationLabel.addTextChangedListener(pricePerWeightTextWatcher);

		// clean product name on long press
		textViewProductsNames.setOnLongClickListener(v -> {
			textViewProductsNames.setText("");
			productInformationLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
			return true;
		});

		// make sure we can click links
		productInformationLabel.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private void searchProductByBarcode(String barcode) {
		try (Cursor results = db.getProductByBarcode(barcode)) {
			if (results.moveToFirst()) {
				productId = results.getInt(0);
				String productName = results.getString(1);
				AutoCompleteTextView textViewProductsNames = findViewById(R.id.autocomplete_product);
				textViewProductsNames.setText(productName);
				setProductInfoLabel(productId);
			} else {
				Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@NonNull
	private TextWatcher getTextWatcher(TextView productInformationLabel, Button updateProductButton) {
		final TextWatcher pricePerWeightTextWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {}
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String pricePerWeightString = productInformationLabel.getText().toString();
				String pricePerKilogram = getResources().getString(R.string.price_per_kilogram);
				String pricePerPound = getResources().getString(R.string.price_per_pound);
				updateProductButton.setEnabled(!pricePerWeightString.equals(pricePerKilogram) && !pricePerWeightString.equals(pricePerPound));
			}
		};
		pricePerWeightTextWatcher.onTextChanged("", 0, 0, 0);
		return pricePerWeightTextWatcher;
	}

	@NonNull
	private static TextWatcher getTextWatcher(Button calculateButton, EditText price, EditText weight) {
		TextWatcher priceWeightTextWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {}
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				calculateButton.setEnabled(((price.getText() != null) && (!price.getText().toString().isEmpty())) &&
						((weight.getText() != null) && (!weight.getText().toString().isEmpty())));
			}
		};
		priceWeightTextWatcher.onTextChanged("", 0, 0, 0);
		return priceWeightTextWatcher;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (isPreviousActivitySettings) {
			loadPreferences();
		}
		isPreviousActivitySettings = false;
	}

	@Override
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
		SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
		String weightUnitString = sharedPreferences.getString("weightUnit", null);
		if (weightUnitString != null) {
			weightUnit = WeightUnit.fromName(weightUnitString);
		} else {
			weightUnit = WeightUnit.KILOGRAMS;
			String countryCode = Locale.getDefault().getCountry();
			if (countryCode.equals("US") || countryCode.equals("LR") || countryCode.equals("MM")) {
				weightUnit = WeightUnit.POUNDS;
			}
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("weightUnit", weightUnit.toString());
			editor.apply();
		}
		clearFields();
	}

	private void calculatePricePerWeight() {
		float price;
		float weight;
		try {
			price = Float.parseFloat(((EditText) findViewById(R.id.price_entry)).getText().toString());
			weight = Float.parseFloat(((EditText) findViewById(R.id.weight_entry)).getText().toString());
		} catch (Exception e) {
			price = 0;
			weight = 0;
		}
		TextView pricePerWeight = findViewById(R.id.price_per_weight_label);
		DecimalFormat df = new DecimalFormat("####.##");
		if (weightUnit == WeightUnit.POUNDS) {
			float result = (price / weight) * 16;
			if (Float.isNaN(result)) {
				result = 0f;
			}
			String pricePerWeightText = getResources().getString(R.string.price_per_pound) + df.format(result).replace(",", ".");
			pricePerWeight.setText(pricePerWeightText);
		} else {
			float result = (price / weight) * 1000;
			if (Float.isNaN(result)) {
				result = 0f;
			}
			String pricePerWeightText = getResources().getString(R.string.price_per_kilogram) + df.format(result).replace(",", ".");
			pricePerWeight.setText(pricePerWeightText);
		}
	}

	private void setProductInfoLabel(int productId) {
		final TextView productInformationLabel = findViewById(R.id.product_information_label);
		try (DbHelper helper = new DbHelper(this);
			 Cursor results = helper.getProduct(productId)) {
			if (results.moveToFirst()) {
				String pricePerString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
				String pricePer = results.getString(2);
				String place = results.getString(3);
				String url = results.getString(4);
				if ((url != null) && (!url.isEmpty())) {
					place = "<a href=\"" + url + "\">" + place + "</a>";
				}
				productInformationLabel.setText(Html.fromHtml(pricePerString + pricePer + " @ " + place, Html.FROM_HTML_MODE_LEGACY));
			}
		}
	}

	private AutoCompleteTextView refreshProducts() {
		ArrayList<Product> products = new ArrayList<>();
		try (DbHelper helper = new DbHelper(this);
			 Cursor results = helper.getProductNames()) {
			while (results.moveToNext()) {
				products.add(Product.fromCursor(results));
			}
		}

		ArrayAdapter<Product> adapter = new ArrayAdapter<>(this, R.layout.simple_drop_down, products);
		final AutoCompleteTextView textViewProductsNames = findViewById(R.id.autocomplete_product);
		textViewProductsNames.setAdapter(adapter);

		return textViewProductsNames;
	}

	private void clearFields() {
		final EditText price = findViewById(R.id.price_entry);
		price.setText("");
		final EditText weight = findViewById(R.id.weight_entry);
		weight.setText("");
		String weightString = getResources().getString(weightUnit == WeightUnit.KILOGRAMS ? R.string.grams : R.string.ounces);
		weight.setHint(weightString);
		final AutoCompleteTextView textViewProductsNames = findViewById(R.id.autocomplete_product);
		textViewProductsNames.setText("");
		final TextView productInformationLabel = findViewById(R.id.product_information_label);
		productInformationLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
		TextView pricePerWeightLabel = findViewById(R.id.price_per_weight_label);
		pricePerWeightLabel.setText(weightUnit == WeightUnit.KILOGRAMS ? R.string.price_per_kilogram : R.string.price_per_pound);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (db != null) {
			db.close();
		}
	}
}
