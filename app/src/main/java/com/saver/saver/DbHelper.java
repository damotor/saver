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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {
	public static final String DBNAME = "products.db";
	public static final int VERSION = 2;
	private SQLiteDatabase database;

	public DbHelper(Context context) {
		super(context, DBNAME, null, VERSION);
		try {
			database = this.getWritableDatabase();
		} catch (Exception e) {
			Log.e("DbHelper", "Failed to get writable database.", e);
			database = this.getReadableDatabase();
		}
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d("DbHelper", "onCreate called, creating tables.");
		db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, barcode TEXT);");
		db.execSQL("CREATE TABLE IF NOT EXISTS product_revisions (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, original_created_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, barcode TEXT);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT;");
			db.execSQL("ALTER TABLE product_revisions ADD COLUMN barcode TEXT;");
		}
	}

	public Cursor getProductNames() {
		return database.rawQuery("SELECT id, name FROM products", null);
	}

	public Cursor getProduct(int id) {
		return database.rawQuery("SELECT id, name, price_per_weight, place, url, created_at, barcode FROM products WHERE id = ?", new String[] { String.valueOf(id) });
	}

	public Cursor getProductByBarcode(String barcode) {
		return database.rawQuery("SELECT id, name, price_per_weight, place, url, created_at, barcode FROM products WHERE barcode = ?", new String[] { barcode });
	}

	public Cursor getProductRevisionPricesPerWeight(String name) {
		return database.rawQuery("SELECT price_per_weight FROM product_revisions WHERE name = ? ORDER BY original_created_at ASC", new String[] { name });
	}

	public Cursor getProductHistory(String name) {
		return database.rawQuery("SELECT price_per_weight, place, original_created_at FROM product_revisions WHERE name = ? ORDER BY original_created_at DESC", new String[] { name });
	}

	public Cursor getPlaces() {
		return database.rawQuery("SELECT DISTINCT place FROM products ORDER BY place", null);
	}

	public Integer addProduct(String name, float pricePerWeight, String place, String url, String barcode) {
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("price_per_weight", pricePerWeight);
		values.put("place", place);
		values.put("url", url);
		values.put("barcode", barcode);
		database.insert("products", null, values);
		return getMaxProductId();
	}

	public Integer updateProduct(int id, String name, float pricePerWeight, String place, String url, String barcode) {
		try (Cursor results = getProduct(id)) {
			if (results.moveToFirst()) {
				String oldName = results.getString(1);
				float oldPricePerWeight = results.getFloat(2);
				String oldPlace = results.getString(3);
				String oldUrl = results.getString(4);
				String oldCreatedAt = results.getString(5);
				String oldBarcode = results.getString(6);

				addProductRevision(oldName, oldPricePerWeight, oldPlace, oldUrl, oldCreatedAt, oldBarcode);
			}
		}

		deleteProduct(id);

		return addProduct(name, pricePerWeight, place, url, barcode);
	}

	@Override
	public synchronized void close() {
		super.close();
		if (database != null && database.isOpen()) {
			database.close();
		}
	}

	private Integer getMaxProductId() {
		try (Cursor results = database.rawQuery("SELECT id FROM products ORDER BY id DESC LIMIT 1", null)) {
			if (!results.moveToFirst()) {
				return 0;
			}
			return results.getInt(0);
		}
	}

	public void addProductRevision(String name, float pricePerWeight, String place, String url, String original_created_at, String barcode) {
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("price_per_weight", pricePerWeight);
		values.put("place", place);
		values.put("url", url);
		values.put("original_created_at", original_created_at);
		values.put("barcode", barcode);
		database.insert("product_revisions", null, values);
	}

	private void deleteProduct(Integer id) {
		database.delete("products", "id = ?", new String[]{id.toString()});
	}

	public void deleteProductButBackup(int id) {
		try (Cursor results = getProduct(id)) {
			if (results.moveToFirst()) {
				String oldName = results.getString(1);
				float oldPricePerWeight = results.getFloat(2);
				String oldPlace = results.getString(3);
				String oldUrl = results.getString(4);
				String oldCreatedAt = results.getString(5);
				String oldBarcode = results.getString(6);

				addProductRevision(oldName, oldPricePerWeight, oldPlace, oldUrl, oldCreatedAt, oldBarcode);
			}
		}
		deleteProduct(id);
	}

	public boolean exportDatabase(Context context) {
		if (!database.isOpen()) {
			Log.e("DbHelper", "Export failed: Database is not open.");
			return false;
		}

		File privateDbFile = new File(database.getPath());
		if (!privateDbFile.exists()) {
			Log.e("DbHelper", "Export failed: Private database file does not exist.");
			return false;
		}

		ContentResolver resolver = context.getContentResolver();
		ContentValues contentValues = new ContentValues();
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, DBNAME);
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.sqlite3");
		contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

		Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
		Uri newFileUri = resolver.insert(collection, contentValues);

		if (newFileUri == null) {
			Log.e("DbHelper", "Export failed: Could not create MediaStore entry.");
			return false;
		}

		try (InputStream in = Files.newInputStream(privateDbFile.toPath());
			 OutputStream out = resolver.openOutputStream(newFileUri)) {
			if (out == null) {
				Log.e("DbHelper", "Export failed: Could not open output stream for URI.");
				return false;
			}
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			Log.i("DbHelper", "Database exported successfully to " + newFileUri);
			return true;
		} catch (Exception e) {
			Log.e("DbHelper", "Export failed with exception.", e);
			resolver.delete(newFileUri, null, null);
			return false;
		}
	}

	public boolean importDatabase(Context context, Uri sourceUri) {
		File privateDbFile = context.getDatabasePath(DBNAME);

		close();

		try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
			 OutputStream out = new FileOutputStream(privateDbFile, false)) {
			if (in == null) {
				Log.e("DbHelper", "Import failed: Could not open input stream from URI.");
				return false;
			}
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			Log.i("DbHelper", "Database imported successfully from " + sourceUri);

			database = SQLiteDatabase.openDatabase(privateDbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);

			return true;
		} catch (Exception e) {
			Log.e("DbHelper", "Import failed with exception.", e);
			database = this.getWritableDatabase();
			return false;
		}
	}

	public String getDatabasePath() {
		return database.getPath();
	}
}
