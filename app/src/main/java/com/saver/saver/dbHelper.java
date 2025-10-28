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
import java.io.FileInputStream;
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
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class dbHelper extends SQLiteOpenHelper {
	public static final String DBNAME = "products.db";
	public static final int VERSION = 1;
	SQLiteDatabase database = null;

	public dbHelper(Context context) {
		// This is the standard, correct way to use SQLiteOpenHelper.
		// It will automatically create the database in the app's private internal storage.
		// Path: /data/data/com.saver.saver/databases/products.db
		super(context, DBNAME, null, VERSION);

		try {
			// Get a writable database. This single call handles creation, opening, and upgrades.
			database = this.getWritableDatabase();
		} catch (Exception e) {
			Log.e("dbHelper", "Failed to get writable database.", e);
			// As a last resort, try to get a readable one.
			database = this.getReadableDatabase();
		}
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// This method is called automatically by getWritableDatabase() only if the database doesn't exist.
		Log.d("dbHelper", "onCreate called, creating tables.");
		db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);");
		db.execSQL("CREATE TABLE IF NOT EXISTS product_revisions (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, original_created_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// For future schema changes
		switch(oldVersion){
			case 1:
				//db.execSQL("ALTER TABLE ...");
		}
	}

	// --- All other methods remain the same ---
	// They will correctly operate on the 'database' object.

	public Cursor getProductNames() {
		return database.rawQuery("SELECT id, name FROM products", null);
	}

	public Cursor getProduct(int id) {
		return database.rawQuery("SELECT id, name, price_per_weight, place, url, created_at FROM products WHERE id = ?", new String[] { String.valueOf(id) });
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

	public Integer addProduct(String name, float pricePerWeight, String place, String url) {
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("price_per_weight", pricePerWeight);
		values.put("place", place);
		values.put("url", url);
		database.insert("products", null, values);
		return getMaxProductId();
	}

	public Integer updateProduct(int id, String name, float pricePerWeight, String place, String url) {
		Cursor results = getProduct(id);
		results.moveToFirst();
		if (results.isAfterLast() == false) {
			String oldName = results.getString(1);
			float oldPricePerWeight = results.getFloat(2);
			String oldPlace = results.getString(3);
			String oldUrl = results.getString(4);
			String oldCreatedAt = results.getString(5);

			addProductRevision(oldName, oldPricePerWeight, oldPlace, oldUrl, oldCreatedAt);
		}
		results.close();

		deleteProduct(id);

		return addProduct(name, pricePerWeight, place, url);
	}

	public String getDatabasePath() {
		return database.getPath();
	}

	public void close() {
		if (database != null && database.isOpen()) {
			database.close();
		}
	}

	private Integer getMaxProductId() {
		Cursor results = database.rawQuery("SELECT id FROM products ORDER BY id DESC LIMIT 1", null);
		if (results == null || !results.moveToFirst()) {
			return 0; // Return a default value if no products exist
		}
		Integer maxId = results.getInt(0);
		results.close();
		return maxId;
	}

	public void addProductRevision(String name, float pricePerWeight, String place, String url, String original_created_at) {
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("price_per_weight", pricePerWeight);
		values.put("place", place);
		values.put("url", url);
		values.put("original_created_at", original_created_at);
		database.insert("product_revisions", null, values);
	}

	private void deleteProduct(Integer id) {
		database.delete("products", "id = ?", new String[]{id.toString()});
	}

	public void deleteProductButBackup(int id) {
		Cursor results = getProduct(id);
		results.moveToFirst();
		if (results.isAfterLast() == false) {
			String oldName = results.getString(1);
			float oldPricePerWeight = results.getFloat(2);
			String oldPlace = results.getString(3);
			String oldUrl = results.getString(4);
			String oldCreatedAt = results.getString(5);

			addProductRevision(oldName, oldPricePerWeight, oldPlace, oldUrl, oldCreatedAt);
		}
		results.close();

		deleteProduct(id);
	}

	/**
	 * Exports the app's private database to the public "Downloads" folder.
	 * @param context The application context.
	 * @return True if export was successful, false otherwise.
	 */
	public boolean exportDatabase(Context context) {
		if (!database.isOpen()) {
			Log.e("dbHelper", "Export failed: Database is not open.");
			return false;
		}

		// Get the path of the app's private database file
		File privateDbFile = new File(database.getPath());
		if (!privateDbFile.exists()) {
			Log.e("dbHelper", "Export failed: Private database file does not exist.");
			return false;
		}

		ContentResolver resolver = context.getContentResolver();
		ContentValues contentValues = new ContentValues();
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, DBNAME);
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.sqlite3"); // A common MIME type for SQLite
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        // Using MediaStore to create an entry in the Downloads folder
		Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
		Uri newFileUri = resolver.insert(collection, contentValues);

		if (newFileUri == null) {
			Log.e("dbHelper", "Export failed: Could not create MediaStore entry.");
			return false;
		}

		try (InputStream in = Files.newInputStream(privateDbFile.toPath());
             OutputStream out = resolver.openOutputStream(newFileUri)) {
			if (out == null) {
				Log.e("dbHelper", "Export failed: Could not open output stream for URI.");
				return false;
			}
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			Log.i("dbHelper", "Database exported successfully to " + newFileUri.toString());
			return true;
		} catch (Exception e) {
			Log.e("dbHelper", "Export failed with exception.", e);
			// Clean up the failed entry if possible
			resolver.delete(newFileUri, null, null);
			return false;
		}
	}

	/**
	 * Imports a database file from a given URI, replacing the current app database.
	 * @param context The application context.
	 * @param sourceUri The content URI of the file to import.
	 * @return True if import was successful, false otherwise.
	 */
	public boolean importDatabase(Context context, Uri sourceUri) {
		File privateDbFile = new File(context.getDatabasePath(DBNAME).getPath());

		// First, close the current database connection
		close();

		try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
			 OutputStream out = new FileOutputStream(privateDbFile, false)) { // Overwrite the existing private DB
			if (in == null) {
				Log.e("dbHelper", "Import failed: Could not open input stream from URI.");
				return false;
			}
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			Log.i("dbHelper", "Database imported successfully from " + sourceUri.toString());

			// Re-open the database after successful import
			database = SQLiteDatabase.openDatabase(privateDbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);

			return true;
		} catch (Exception e) {
			Log.e("dbHelper", "Import failed with exception.", e);
			// If import fails, try to restore the original connection
			database = this.getWritableDatabase();
			return false;
		}
	}
}
