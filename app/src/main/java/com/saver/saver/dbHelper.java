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

import java.io.File;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

public class dbHelper extends SQLiteOpenHelper {
	public static final String DBNAME = "products.db";
	public static final int VERSION = 1;
	File sdcard = Environment.getExternalStorageDirectory();
	String dbfile = sdcard.getAbsolutePath() + File.separator + DBNAME;
	SQLiteDatabase database = null;

	public dbHelper(Context context) {
		super(context, DBNAME, null, VERSION);
		try {
			File dbFile = new File(dbfile);
			if (dbFile.exists()) {
				database = SQLiteDatabase.openDatabase(dbfile, null,SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			} else {
				File dir = Environment.getExternalStorageDirectory();
				if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
					database = SQLiteDatabase.openOrCreateDatabase(dbfile, null);
				} else {
					database = this.getWritableDatabase();
				}
			}
		} catch (Exception E) {
			database = this.getWritableDatabase();
		}

		Cursor cursor = database.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = 'products'", null);
		if(cursor!=null) {
			if(cursor.getCount() == 0) {
				onCreate(database);
			}
			cursor.close();
		}
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);");
		db.execSQL("CREATE TABLE IF NOT EXISTS product_revisions (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_per_weight NUMERIC NOT NULL, place TEXT NOT NULL, url TEXT, original_created_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		switch(oldVersion){
			case 1:
				//db.execSQL("");
		}
	}

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
		database.close();
	}

	private Integer getMaxProductId() {
		Cursor results = database.rawQuery("SELECT id FROM products ORDER BY id DESC LIMIT 1", null);
		results.moveToFirst();
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
}