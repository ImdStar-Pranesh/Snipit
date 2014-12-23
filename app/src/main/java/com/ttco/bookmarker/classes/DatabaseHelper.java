package com.ttco.bookmarker.classes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String databaseName = "bookmarker.db";
    public static final int version = 1;

    public static final String BOOK_TABLE = "book";

    public static final String B_ID = "book_id";
    public static final String B_TITLE = "title";
    public static final String B_AUTHOR = "author";
    public static final String B_DATE_ADDED = "date_added";
    public static final String B_COLOR_CODE = "color_code";
    public static final String B_ORDER = "book_order";

    public static final String BOOKMARK_TABLE = "bookmark";

    public static final String BM_ID = "bookmark_id";
    public static final String BM_BOOK_FOREIGN_KEY = "book_id";
    public static final String BM_NAME = "name";
    public static final String BM_PAGENUMBER = "page_number";
    public static final String BM_IMAGEPATH = "image_path";
    public static final String BM_DATE_ADDED = "date_added";
    public static final String BM_VIEWS = "views";
    public static final String BM_ORDER = "bookmark_order";

    private Context context;

    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, DatabaseErrorHandler errorHandler) {
        super(context, name, factory, version, errorHandler);
    }

    public DatabaseHelper(Context context) {
        super(context, databaseName, null, version);
        this.context = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String CREATE_BOOK_TABLE = "CREATE TABLE IF NOT EXISTS " + BOOK_TABLE
                + " (" + B_ID + " INTEGER PRIMARY KEY, " + B_TITLE + " TEXT, " + B_AUTHOR + " TEXT, " + B_DATE_ADDED + " TEXT, " + B_COLOR_CODE + " INTEGER, " + B_ORDER + " INTEGER)";

        String CREATE_BOOKMARK_TABLE = "CREATE TABLE IF NOT EXISTS " + BOOKMARK_TABLE
                + " (" + BM_ID + " INTEGER PRIMARY KEY, " + BM_BOOK_FOREIGN_KEY + " INTEGER, " + BM_NAME + " TEXT, " + BM_PAGENUMBER + " INTEGER, " + BM_IMAGEPATH + " TEXT, " + BM_DATE_ADDED + " TEXT, " + BM_ORDER + " INTEGER, " + BM_VIEWS + " INTEGER, FOREIGN KEY (" + BM_BOOK_FOREIGN_KEY + ") REFERENCES " + BOOK_TABLE + " (" + B_ID + ") ON DELETE CASCADE)";

        sqLiteDatabase.execSQL(CREATE_BOOK_TABLE);
        sqLiteDatabase.execSQL(CREATE_BOOKMARK_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
    }

    public List<Book> getAllBooks(String sortBy) {
        SQLiteDatabase dbHandler = this.getReadableDatabase();

        List<Book> books = new ArrayList<Book>();

        String query = "";

        if (sortBy == null)
            query = "SELECT * FROM " + BOOK_TABLE + " ORDER BY " + B_ORDER;
        else
            query = "SELECT * FROM " + BOOK_TABLE + " ORDER BY " + sortBy;

        Cursor cursor = dbHandler.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Book book = new Book();
                book.setId(cursor.getInt(0));
                book.setTitle(cursor.getString(1));
                book.setAuthor(cursor.getString(2));
                book.setDate_added(cursor.getString(3));
                book.setColorCode(cursor.getInt(4));
                book.setOrder(cursor.getInt(5));
                books.add(book);
            } while (cursor.moveToNext());
        }

        cursor.close();
        dbHandler.close();

        return books;
    }


    public int createBook(Book book) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();
        ContentValues cv;

        cv = new ContentValues();
        cv.putNull(B_ID);
        cv.put(B_TITLE, book.getTitle());
        cv.put(B_AUTHOR, book.getAuthor());
        cv.put(B_DATE_ADDED, book.getDate_added());
        cv.put(B_COLOR_CODE, book.getColorCode());
        cv.put(B_ORDER, getMax_BookOrder(dbHandler));

        int lastInsertRowId = (int) dbHandler.insert(BOOK_TABLE, null, cv);

        dbHandler.close();

        return lastInsertRowId;
    }

    public void updateBook(Book book) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();

        ContentValues newValues = new ContentValues();

        String[] args = new String[]{String.valueOf(book.getId())};

        newValues.put(B_TITLE, book.getTitle());
        newValues.put(B_AUTHOR, book.getAuthor());
        newValues.put(B_DATE_ADDED, book.getDate_added());
        newValues.put(B_ORDER, book.getOrder());

        dbHandler.update(BOOK_TABLE, newValues, B_ID + "= ?", args);
        dbHandler.close();
    }


    public void deleteBook(int book_id) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();

        String table = BOOK_TABLE;
        String whereClause = B_ID + " = ?";
        String[] whereArgs = new String[]{String.valueOf(book_id)};
        dbHandler.delete(table, whereClause, whereArgs);
    }

    public List<Bookmark> getAllBookmarks(int book_id, String sortBy) {
        SQLiteDatabase dbHandler = this.getReadableDatabase();

        List<Bookmark> bookmarks = new ArrayList<Bookmark>();

        String query = "";

        if (sortBy == null)
            query = "SELECT * FROM " + BOOKMARK_TABLE + " WHERE " + BM_BOOK_FOREIGN_KEY + " = " + book_id + " ORDER BY " + BM_ORDER;
        else
            query = "SELECT * FROM " + BOOKMARK_TABLE + " WHERE " + BM_BOOK_FOREIGN_KEY + " = " + book_id + " ORDER BY " + sortBy;

        Cursor cursor = dbHandler.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Bookmark bookmark = new Bookmark();
                bookmark.setId(cursor.getInt(0));
                bookmark.setBookId(cursor.getInt(1));
                bookmark.setName(cursor.getString(2));
                bookmark.setPage_number(cursor.getInt(3));
                bookmark.setImage_path(cursor.getString(4));
                bookmark.setDate_added(cursor.getString(5));
                bookmark.setOrder(cursor.getInt(6));
                bookmark.setViews(cursor.getInt(7));
                bookmarks.add(bookmark);
            } while (cursor.moveToNext());
        }

        cursor.close();
        dbHandler.close();

        return bookmarks;
    }

    public void createBookmark(Bookmark bookmark, int book_id) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();
        ContentValues cv;

        cv = new ContentValues();
        cv.putNull(BM_ID);
        cv.put(BM_BOOK_FOREIGN_KEY, book_id);
        cv.put(BM_NAME, bookmark.getName());
        cv.put(BM_PAGENUMBER, bookmark.getPage_number());
        cv.put(BM_IMAGEPATH, bookmark.getImage_path());
        cv.put(BM_DATE_ADDED, bookmark.getDate_added());
        cv.put(BM_ORDER, getMax_BookmarkOrder(dbHandler));
        cv.put(BM_VIEWS, getBookmarkViews(dbHandler));

        dbHandler.insert(BOOKMARK_TABLE, null, cv);
        dbHandler.close();
    }

    public void updateBookmark(Bookmark bookmark) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();

        ContentValues newValues = new ContentValues();

        String[] args = new String[]{String.valueOf(bookmark.getId())};

        newValues.put(BM_NAME, bookmark.getName());
        newValues.put(BM_PAGENUMBER, bookmark.getPage_number());
        newValues.put(BM_DATE_ADDED, bookmark.getDate_added());
        newValues.put(BM_IMAGEPATH, bookmark.getImage_path());
        newValues.put(BM_ORDER, bookmark.getOrder());
        newValues.put(BM_VIEWS, bookmark.getViews());

        dbHandler.update(BOOKMARK_TABLE, newValues, BM_ID + "= ?", args);
        dbHandler.close();
    }

    public void deleteBookmark(int bookmark_id) {
        SQLiteDatabase dbHandler = this.getWritableDatabase();

        String table = BOOKMARK_TABLE;
        String whereClause = BM_ID + " = ?";
        String[] whereArgs = new String[]{String.valueOf(bookmark_id)};
        dbHandler.delete(table, whereClause, whereArgs);
    }

    public int getMax_BookOrder(SQLiteDatabase dbHandler) {
        Cursor cursor = dbHandler.rawQuery("SELECT MAX(" + B_ORDER + ") FROM " + BOOK_TABLE, null);

        int max_book_order = 0;

        if (cursor.moveToFirst())
            max_book_order = cursor.getInt(0) + 1;

        cursor.close();

        return max_book_order;
    }

    public int getMax_BookmarkOrder(SQLiteDatabase dbHandler) {
        Cursor cursor = dbHandler.rawQuery("SELECT MAX(" + BM_ORDER + ") FROM " + BOOKMARK_TABLE, null);

        int max_bookmark_order = 0;

        if (cursor.moveToFirst())
            max_bookmark_order = cursor.getInt(0) + 1;

        cursor.close();

        return max_bookmark_order;
    }

    public int getBookmarkViews(SQLiteDatabase dbHandler){
        Cursor cursor = dbHandler.rawQuery("SELECT " + BM_VIEWS + " FROM " + BOOKMARK_TABLE, null);

        int views = 0;

        if (cursor.moveToFirst())
            views = cursor.getInt(0);

        cursor.close();

        return views;
    }
}
