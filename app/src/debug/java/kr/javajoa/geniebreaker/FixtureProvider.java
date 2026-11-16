package kr.javajoa.geniebreaker;

public class FixtureProvider extends android.content.ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(android.net.Uri uri) { return null; }
    @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(android.net.Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(android.net.Uri uri, android.content.ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
