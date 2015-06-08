package com.om.atomic.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.melnykov.fab.FloatingActionButton;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.nispok.snackbar.listeners.EventListener;
import com.om.atomic.R;
import com.om.atomic.classes.Bookmark;
import com.om.atomic.classes.Constants;
import com.om.atomic.classes.DatabaseHelper;
import com.om.atomic.classes.EventBus_Poster;
import com.om.atomic.classes.EventBus_Singleton;
import com.om.atomic.classes.Helper_Methods;
import com.om.atomic.classes.Param;
import com.om.atomic.classes.RoundedTransform;
import com.om.atomic.dragsort_listview.DragSortListView;
import com.om.atomic.showcaseview.ShowcaseView;
import com.om.atomic.showcaseview.ViewTarget;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import net.frakbot.jumpingbeans.JumpingBeans;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import hugo.weaving.DebugLog;
import me.grantland.widget.AutofitTextView;

public class Bookmarks_Activity extends Base_Activity implements SearchView.OnQueryTextListener {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    @InjectView(R.id.createNewBookmarkBTN)
    FloatingActionButton createNewBookmarkBTN;
    @InjectView(R.id.emptyListLayout)
    RelativeLayout emptyListLayout;
    @InjectView(R.id.bookmarksList)
    DragSortListView listView;

    private Bookmark tempBookmark;

    private SearchView searchView;

    private DragSortListView.DropListener onDrop =
            new DragSortListView.DropListener() {
                @Override
                public void drop(int from, int to) {
                    bookmarksAdapter.notifyDataSetChanged();
                }
            };
    private Bookmarks_Adapter bookmarksAdapter;
    private int book_id;
    private DragSortListView.RemoveListener onRemove = new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
            ArrayList<Bookmark> tempBookmarks = dbHelper.getAllBookmarks(book_id, null);
            dbHelper.deleteBookmark(tempBookmarks.get(which).getId());
            handleEmptyOrPopulatedScreen(tempBookmarks);

            EventBus_Singleton.getInstance().post(new EventBus_Poster("bookmark_changed"));
        }
    };
    private DragSortListView.DragListener onDrag = new DragSortListView.DragListener() {
        @Override
        public void drag(int from, int to) {
            bookmarksAdapter.swap(from, to);
            String sorting_type_pref = prefs.getString(Constants.SORTING_TYPE_PREF, Constants.SORTING_TYPE_NOSORT);

            if (!sorting_type_pref.equals(Constants.SORTING_TYPE_NOSORT)) {
                prefsEditor.putString(Constants.SORTING_TYPE_PREF, Constants.SORTING_TYPE_NOSORT);
                prefsEditor.commit();
                prepareForNotifyDataChanged(book_id);

                Crouton.makeText(Bookmarks_Activity.this, R.string.sort_order_override, Style.ALERT).show();
            }
        }
    };

    static final int DELETE_BOOKMARK_ANIMATION_DURATION = 300;

    private Snackbar undoDeleteBookmarkSB;
    private boolean itemPendingDeleteDecision = false;

    private final static int SHOW_CREATE_BOOKMARK_SHOWCASE = 1;

    private String book_title;
    private int book_color_code;
    private ShowcaseView createBookmarkShowcase;
    private ArrayList<Bookmark> bookmarks;
    private DatabaseHelper dbHelper;
    private String mCurrentPhotoPath;
    private Uri photoFileUri;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private Helper_Methods helperMethods;
    private Handler UIHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        ButterKnife.inject(this);

        UIHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SHOW_CREATE_BOOKMARK_SHOWCASE:
                        showCreateBookmarkShowcase();
                        break;
                }
                super.handleMessage(msg);
            }
        };

        EventBus_Singleton.getInstance().register(this);

        helperMethods = new Helper_Methods(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();
        prefsEditor.apply();

        dbHelper = new DatabaseHelper(this);

        Helper_Methods helperMethods = new Helper_Methods(this);

        mCurrentPhotoPath = constructImageFilename();

        book_id = getIntent().getExtras().getInt(Constants.EXTRAS_BOOK_ID);
        book_title = getIntent().getStringExtra(Constants.EXTRAS_BOOK_TITLE);
        book_color_code = getIntent().getExtras().getInt(Constants.EXTRAS_BOOK_COLOR);

        String sorting_type_pref = prefs.getString(Constants.SORTING_TYPE_PREF, Constants.SORTING_TYPE_NOSORT);
        if (sorting_type_pref != null) {
            if (sorting_type_pref.equals(Constants.SORTING_TYPE_NOSORT)) {
                bookmarks = dbHelper.getAllBookmarks(book_id, null);
            } else {
                bookmarks = dbHelper.getAllBookmarks(book_id, sorting_type_pref);
            }
        }

        handleEmptyOrPopulatedScreen(bookmarks);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(book_title);
        helperMethods.setUpActionbarColors(this, book_color_code);

        createNewBookmarkBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (createBookmarkShowcase != null)
                    createBookmarkShowcase.hide();

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile(mCurrentPhotoPath);
                        photoFileUri = Uri.fromFile(photoFile);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    if (photoFile != null) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                photoFileUri);
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    }
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Bookmark bookmark = ((Bookmark) listView.getItemAtPosition(position));

                //Clicking on an adview when there's no Internet connection will cause this condition to be satisfied because no Book will be found at the index of that adview
                if (bookmark != null) {
                    if (bookmark.getIsNoteShowing() == 0) {
                        int bookmarkViews = dbHelper.getBookmarkViews(bookmark.getId());
                        bookmark.setViews(bookmarkViews + 1);
                        dbHelper.updateBookmark(bookmark);
                        bookmarksAdapter.notifyDataSetChanged();

                        Intent intent = new Intent(Bookmarks_Activity.this, View_Bookmark_Activity.class);
                        intent.putExtra(Constants.EXTRAS_BOOK_ID, book_id);
                        intent.putExtra(Constants.EXTRAS_BOOK_TITLE, book_title);
                        intent.putExtra(Constants.EXTRAS_CURRENT_BOOKMARK_POSITION, position - 1);
                        intent.putParcelableArrayListExtra("bookmarks", bookmarks);
                        startActivity(intent);
                    }
                }
            }
        });

        createNewBookmarkBTN.attachToListView(listView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        if (!bookmarks.isEmpty()) {
            inflater.inflate(R.menu.bookmarks_activity, menu);

            MenuItem byNumber = menu.getItem(1);
            SpannableString numberString = new SpannableString(byNumber.getTitle().toString());
            numberString.setSpan(new ForegroundColorSpan(Color.BLACK), 0, numberString.length(), 0);
            byNumber.setTitle(numberString);

            MenuItem byName = menu.getItem(2);
            SpannableString nameString = new SpannableString(byName.getTitle().toString());
            nameString.setSpan(new ForegroundColorSpan(Color.BLACK), 0, nameString.length(), 0);
            byName.setTitle(nameString);

            MenuItem byViews = menu.getItem(3);
            SpannableString viewsString = new SpannableString(byViews.getTitle().toString());
            viewsString.setSpan(new ForegroundColorSpan(Color.BLACK), 0, viewsString.length(), 0);
            byViews.setTitle(viewsString);

            MenuItem searchItem = menu.findItem(R.id.search);
            searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            searchView.setOnQueryTextListener(this);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String sortByFormattedForSQL;

        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                if (getCurrentFocus() != null) {
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                break;
            case R.id.search:
                searchView.setIconified(false);
                return true;
            case R.id.sort_page_number:
                sortByFormattedForSQL = "page_number";
                prefsEditor.putString(Constants.SORTING_TYPE_PREF, sortByFormattedForSQL);
                prefsEditor.commit();
                bookmarks = dbHelper.getAllBookmarks(book_id, sortByFormattedForSQL);
                bookmarksAdapter.notifyDataSetChanged();
                break;
            case R.id.sort_by_name:
                sortByFormattedForSQL = "name";
                prefsEditor.putString(Constants.SORTING_TYPE_PREF, sortByFormattedForSQL);
                prefsEditor.commit();
                bookmarks = dbHelper.getAllBookmarks(book_id, sortByFormattedForSQL);
                bookmarksAdapter.notifyDataSetChanged();
                break;
            case R.id.sort_by_views:
                sortByFormattedForSQL = "views";
                prefsEditor.putString(Constants.SORTING_TYPE_PREF, sortByFormattedForSQL);
                prefsEditor.commit();
                bookmarks = dbHelper.getAllBookmarks(book_id, sortByFormattedForSQL);
                bookmarksAdapter.notifyDataSetChanged();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                Intent openCreateBookmark = new Intent(Bookmarks_Activity.this, Crop_Image_Activity.class);
                openCreateBookmark.putExtra(Constants.EXTRAS_BOOK_ID, getIntent().getExtras().getInt(Constants.EXTRAS_BOOK_ID));
                openCreateBookmark.putExtra(Constants.EXTRAS_BOOKMARK_IMAGE_PATH, mCurrentPhotoPath);
                openCreateBookmark.putExtra(Constants.EXTRAS_BOOK_COLOR, book_color_code);
                startActivity(openCreateBookmark);
                break;
        }
    }

    @Subscribe
    public void handle_BusEvents(EventBus_Poster ebp) {
        switch (ebp.getMessage()) {
            case "bookmark_viewed":
                String sorting_type_pref = prefs.getString(Constants.SORTING_TYPE_PREF, Constants.SORTING_TYPE_NOSORT);
                if (sorting_type_pref != null) {
                    if (sorting_type_pref.equals(Constants.SORTING_TYPE_NOSORT)) {
                        bookmarks = dbHelper.getAllBookmarks(book_id, null);
                    } else {
                        bookmarks = dbHelper.getAllBookmarks(book_id, sorting_type_pref);
                    }
                }
                bookmarksAdapter.notifyDataSetChanged();
                break;
            case "bookmark_image_updated":
                Helper_Methods.delete_image_from_disk(ebp.getExtra());
            case "bookmark_changed":
            case "bookmark_note_changed":
                prepareForNotifyDataChanged(book_id);
                bookmarksAdapter.notifyDataSetChanged();
//            if (ebp.getExtra() != null) {
//                if (ebp.getExtra().equals("new_bookmark")) {
//                    listView.smoothScrollToPosition(bookmarksAdapter.getCount() + 1, 0, 500);
//                    YoYo.with(Techniques.Tada)
//                            .duration(1500)
//                            .playOn(bookmarksAdapter
//                                    .getView(bookmarksAdapter.getCount() - 1, null, null));
//                }
//            }
                break;
        }
    }

    @DebugLog
    private String constructImageFilename() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp;

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Atomic");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(Constants.DEBUG_TAG, "failed to create directory");
                return null;
            }
        }

        return mediaStorageDir.getPath() + File.separator + imageFileName;
    }

    @DebugLog
    private File createImageFile(String imagePath) throws IOException {
        return new File(imagePath);
    }

    @DebugLog
    public void prepareForNotifyDataChanged(int book_id) {
        /**
         * If a specific sorting order exists, follow that order when getting the bookmarks
         */
        String sorting_type_pref = prefs.getString(Constants.SORTING_TYPE_PREF, Constants.SORTING_TYPE_NOSORT);

        if (sorting_type_pref != null) {
            if (sorting_type_pref.equals(Constants.SORTING_TYPE_NOSORT)) {
                bookmarks = dbHelper.getAllBookmarks(book_id, null);
            } else {
                bookmarks = dbHelper.getAllBookmarks(book_id, sorting_type_pref);
            }
        }

        handleEmptyUI(bookmarks);
    }

    @DebugLog
    public void handleEmptyOrPopulatedScreen(List<Bookmark> bookmarks) {
        handleEmptyUI(bookmarks);

        bookmarksAdapter = new Bookmarks_Adapter(this);

        listView.setDropListener(onDrop);
        listView.setDragListener(onDrag);
        listView.setRemoveListener(onRemove);

        final View listViewHeaderAd = View.inflate(this, R.layout.bookmarks_list_adview_header, null);
        AdView mAdView = (AdView) listViewHeaderAd.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

//        final LayoutAnimationController controller
//                = AnimationUtils.loadLayoutAnimation(
//                this, R.anim.bookmarks_list_layout_controller);

        //If animations are enabled
//        if (dbHelper.getParam(null, Constants.ANIMATIONS_ENABLED_DATABASE_VALUE)) {
//            new Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    listView.addHeaderView(listViewHeaderAd);
//                    listView.setAdapter(bookmarksAdapter);
//                    listView.setLayoutAnimation(controller);
//                }
//            }, 100);
//        } else {
        listView.addHeaderView(listViewHeaderAd);
        listView.setAdapter(bookmarksAdapter);
//        }
    }

    @DebugLog
    public void showCreateBookmarkShowcase() {
        //When a bookmark is deleted from inside Search Results Activity, leading up to this Activity having zero bookmarks and causing the coachmark to appear when the activity is not in focus. So make sure it is in focus first
        if (!dbHelper.getParam(null, 1)) {

            RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lps.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lps.setMargins(getResources().getDimensionPixelOffset(R.dimen.button_margin_left), 0, 0, getResources().getDimensionPixelOffset(R.dimen.button_margin_bottom));

            ViewTarget target = new ViewTarget(R.id.createNewBookmarkBTN, Bookmarks_Activity.this);

            String showcaseTitle = getString(R.string.create_bookmark_showcase_title);
            String showcaseDescription = getString(R.string.create_bookmark_showcase_description);

            createBookmarkShowcase = new ShowcaseView.Builder(Bookmarks_Activity.this, getResources().getDimensionPixelSize(R.dimen.create_bookmark_showcase_inner_rad), getResources().getDimensionPixelSize(R.dimen.create_bookmark_showcase_outer_rad))
                    .setTarget(target)
                    .setContentTitle(Helper_Methods.fontifyString(showcaseTitle))
                    .setContentText(Helper_Methods.fontifyString(showcaseDescription))
                    .setStyle(R.style.CustomShowcaseTheme)
                    .hasManualPosition(true)
                    .xPostion(getResources().getDimensionPixelSize(R.dimen.create_bookmark_text_x))
                    .yPostion(getResources().getDimensionPixelSize(R.dimen.create_bookmark_text_y))
                    .build();
            createBookmarkShowcase.setButtonPosition(lps);
            createBookmarkShowcase.findViewById(R.id.showcase_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    createBookmarkShowcase.hide();

                    Param param = new Param();
                    param.setNumber(1);
                    param.setValue("True");
                    dbHelper.updateParam(param);

                    handleEmptyUI(bookmarks);
                }
            });
            createBookmarkShowcase.show();
        }
    }

    public void handleEmptyUI(List<Bookmark> bookmarks) {
        //Books are empty and the coachmark has been dismissed
        if (bookmarks.isEmpty() && dbHelper.getParam(null, 1)) {
            emptyListLayout.setVisibility(View.VISIBLE);
            JumpingBeans.with((TextView) emptyListLayout.findViewById(R.id.emptyLayoutMessageTV)).appendJumpingDots().build();
        } else if (bookmarks.isEmpty()) {
            emptyListLayout.setVisibility(View.GONE);
            UIHandler.sendEmptyMessageDelayed(SHOW_CREATE_BOOKMARK_SHOWCASE, 200);
        } else {
            emptyListLayout.setVisibility(View.INVISIBLE);
        }
        invalidateOptionsMenu();
    }

    private void deleteCell(final View v, final int index) {
        BookmarksViewHolder vh = (BookmarksViewHolder) v.getTag();
        vh.needInflate = true;

        tempBookmark = bookmarks.get(index);

        itemPendingDeleteDecision = true;

        Animation.AnimationListener collapseAL = new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation arg0) {
                EventBus_Singleton.getInstance().post(new EventBus_Poster("bookmark_changed"));

                Spannable sentenceToSpan = new SpannableString(getResources().getString(R.string.delete_book_confirmation_message) + " " + bookmarks.get(index).getName());

                sentenceToSpan.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                bookmarks.remove(index);
                handleEmptyUI(bookmarks);
                bookmarksAdapter.notifyDataSetChanged();

                undoDeleteBookmarkSB =
                        Snackbar.with(getApplicationContext())
                                .actionLabel(R.string.undo_deletion_title)
                                        //So that we differentiate between explicitly dismissing the snackbar and having it go away due to pressing UNDO
                                .dismissOnActionClicked(false)
                                .duration(8000)
                                .actionColor(getResources().getColor(R.color.yellow))
                                .text(sentenceToSpan)
                                .eventListener(new EventListener() {
                                    @Override
                                    public void onShow(Snackbar snackbar) {
                                    }

                                    @Override
                                    public void onShowByReplace(Snackbar snackbar) {
                                    }

                                    @Override
                                    public void onShown(Snackbar snackbar) {
                                    }

                                    @Override
                                    public void onDismiss(Snackbar snackbar) {
                                        if (itemPendingDeleteDecision) {
                                            finalizeBookmarkDeletion(tempBookmark);
                                        }
                                    }

                                    @Override
                                    public void onDismissByReplace(Snackbar snackbar) {
                                    }

                                    @Override
                                    public void onDismissed(Snackbar snackbar) {
                                    }
                                }).actionListener(new ActionClickListener() {
                            @Override
                            public void onActionClicked(Snackbar snackbar) {
                                prepareForNotifyDataChanged(book_id);
                                bookmarksAdapter.notifyDataSetChanged();

                                itemPendingDeleteDecision = false;
                                undoDeleteBookmarkSB.dismiss();
                            }
                        });

                undoDeleteBookmarkSB.show(Bookmarks_Activity.this);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }
        };

        collapse(v, collapseAL);
    }

    private void collapse(final View v, Animation.AnimationListener al) {
        final int initialHeight = v.getMeasuredHeight();

        Animation anim = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        if (al != null) {
            anim.setAnimationListener(al);
        }

        anim.setDuration(DELETE_BOOKMARK_ANIMATION_DURATION);
        v.startAnimation(anim);
    }

    public void finalizeBookmarkDeletion(Bookmark tempBookmark) {
        Helper_Methods.delete_image_from_disk(tempBookmark.getImage_path());
        dbHelper.deleteBookmark(tempBookmark.getId());
        prepareForNotifyDataChanged(tempBookmark.getBookId());
        bookmarksAdapter.notifyDataSetChanged();
        itemPendingDeleteDecision = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (itemPendingDeleteDecision) {

            finalizeBookmarkDeletion(tempBookmark);

            if (undoDeleteBookmarkSB.isShowing()) {
                undoDeleteBookmarkSB.dismiss();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus_Singleton.getInstance().unregister(this);
    }

    @Override
    public boolean onQueryTextSubmit(String searchTerm) {
        Intent openSearchActivity = new Intent(Bookmarks_Activity.this, SearchResults_Activity.class);
        openSearchActivity.putExtra(Constants.EXTRAS_BOOK_ID, book_id);
        openSearchActivity.putExtra(Constants.EXTRAS_BOOK_TITLE, book_title);
        openSearchActivity.putExtra(Constants.EXTRAS_BOOK_COLOR, book_color_code);
        openSearchActivity.putExtra(Constants.EXTRAS_SEARCH_TERM, searchTerm);
        startActivity(openSearchActivity);

        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        return false;
    }

    private class Bookmarks_Adapter extends BaseAdapter {

        private LayoutInflater inflater;
        private Context context;
        private BookmarksViewHolder holder;
        private DatabaseHelper dbHelper;

        public Bookmarks_Adapter(Context context) {
            super();
            this.context = context;

            this.dbHelper = new DatabaseHelper(context);
            this.inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return bookmarks.size();
        }

        @Override
        public Object getItem(int i) {
            return bookmarks.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            final View parentView;

            if (convertView == null || ((BookmarksViewHolder) convertView.getTag()).needInflate) {
                parentView = inflater.inflate(R.layout.list_item_bookmark, parent, false);

                holder = new BookmarksViewHolder();

                holder.bookmarkName = (AutofitTextView) parentView.findViewById(R.id.bookmarkNameTV);
                holder.bookmarkAction = (Button) parentView.findViewById(R.id.bookmarkAction);
                holder.bookmarkIMG = (ImageView) parentView.findViewById(R.id.bookmarkIMG);
                holder.bookmarkViews = (TextView) parentView.findViewById(R.id.bookmarkViewsTV);
                holder.bookmarkNoteBTN = (Button) parentView.findViewById(R.id.bookmarkNoteBTN);
                holder.bookmarkNoteTV = (AutofitTextView) parentView.findViewById(R.id.bookmarkNoteTV);
                holder.motherView = (RelativeLayout) parentView.findViewById(R.id.list_item_bookmark);
                holder.needInflate = false;

                parentView.setTag(holder);
            } else {
                parentView = convertView;
            }

            holder = (BookmarksViewHolder) parentView.getTag();

            //If the bookmark doesn't have a note
            if (TextUtils.isEmpty(bookmarks.get(position).getNote())) {
                holder.bookmarkNoteBTN.setVisibility(View.INVISIBLE);
            } else {
                holder.bookmarkNoteBTN.setVisibility(View.VISIBLE);
            }

            if (bookmarks.get(position).getIsNoteShowing() == 0) {
                ((GradientDrawable) ((LayerDrawable) holder.motherView.getBackground()).findDrawableByLayerId(R.id.innerView)).setColor(context.getResources().getColor(R.color.white));

                holder.bookmarkAction.setAlpha(1f);
                holder.bookmarkAction.setVisibility(View.VISIBLE);
                holder.bookmarkIMG.setAlpha(1f);
                holder.bookmarkIMG.setVisibility(View.VISIBLE);
                holder.bookmarkViews.setAlpha(1f);
                holder.bookmarkViews.setVisibility(View.VISIBLE);
                holder.bookmarkName.setVisibility(View.VISIBLE);
                holder.bookmarkName.setAlpha(1f);
                holder.bookmarkNoteBTN.setBackground(context.getResources().getDrawable(R.drawable.gray_bookmark));
            } else {
                ((GradientDrawable) ((LayerDrawable) holder.motherView.getBackground()).findDrawableByLayerId(R.id.innerView)).setColor(context.getResources().getColor(helperMethods.determineNoteViewBackground(book_color_code)));

                holder.bookmarkNoteTV.setText(bookmarks.get(position).getNote());
                holder.bookmarkAction.setVisibility(View.INVISIBLE);
                holder.bookmarkIMG.setVisibility(View.INVISIBLE);
                holder.bookmarkViews.setVisibility(View.INVISIBLE);
                holder.bookmarkName.setVisibility(View.INVISIBLE);
                holder.bookmarkNoteTV.setVisibility(View.VISIBLE);
                holder.bookmarkNoteBTN.setBackground(context.getResources().getDrawable(R.drawable.white_bookmark));
            }

            holder.bookmarkName.setText(bookmarks.get(position).getName());
            holder.bookmarkViews.setText(context.getResources().getText(R.string.bookmark_views_label) + " " + bookmarks.get(position).getViews());

            if (bookmarks.get(position).getImage_path().contains("http")) {
                Picasso.with(Bookmarks_Activity.this).load(bookmarks.get(position).getImage_path()).resize(context.getResources().getDimensionPixelSize(R.dimen.bookmark_thumb_width), context.getResources().getDimensionPixelSize(R.dimen.bookmark_thumb_height)).centerCrop().transform(new RoundedTransform(context.getResources().getDimensionPixelSize(R.dimen.bookmark_image_shape_corners_radius), context.getResources().getDimensionPixelSize(R.dimen.bookmark_image_shape_corners_padding_bottom))).error(context.getResources().getDrawable(R.drawable.bookmark_not_found)).noPlaceholder().into(holder.bookmarkIMG);
            } else
                Picasso.with(Bookmarks_Activity.this).load(new File(bookmarks.get(position).getImage_path())).resize(context.getResources().getDimensionPixelSize(R.dimen.bookmark_thumb_width), context.getResources().getDimensionPixelSize(R.dimen.bookmark_thumb_height)).centerCrop().transform(new RoundedTransform(context.getResources().getDimensionPixelSize(R.dimen.bookmark_image_shape_corners_radius), context.getResources().getDimensionPixelSize(R.dimen.bookmark_image_shape_corners_padding_bottom))).error(context.getResources().getDrawable(R.drawable.bookmark_not_found)).noPlaceholder().into(holder.bookmarkIMG);

            holder.bookmarkAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final View overflowButton = view;
                    overflowButton.setBackground(context.getResources().getDrawable(R.drawable.menu_overflow_focus));

                    PopupMenu popup = new PopupMenu(context, view);
                    popup.getMenuInflater().inflate(R.menu.bookmark_list_item,
                            popup.getMenu());
                    for (int i = 0; i < popup.getMenu().size(); i++) {
                        MenuItem item = popup.getMenu().getItem(i);
                        SpannableString spanString = new SpannableString(item.getTitle().toString());
                        spanString.setSpan(new ForegroundColorSpan(Color.BLACK), 0, spanString.length(), 0);
                        item.setTitle(spanString);
                    }
                    popup.show();
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.edit:
                                    Intent editBookmarkIntent = new Intent(Bookmarks_Activity.this, Create_Bookmark_Activity.class);
                                    editBookmarkIntent.putExtra(Constants.EDIT_BOOKMARK_PURPOSE_STRING, Constants.EDIT_BOOKMARK_PURPOSE_VALUE);
                                    editBookmarkIntent.putExtra(Constants.EXTRAS_BOOK_COLOR, book_color_code);
                                    editBookmarkIntent.putExtra("bookmark", bookmarks.get(position));
                                    startActivity(editBookmarkIntent);
                                    break;
                                case R.id.delete:
                                    //Dissmiss the UNDO Snackbar and handle the deletion of the previously awaiting item yourself
                                    if (undoDeleteBookmarkSB != null && undoDeleteBookmarkSB.isShowing()) {
                                        //Careful about position that is passed from the adapter! This has to be accounted for again by using getItemAtPosition because there's an adview among the views
                                        dbHelper.deleteBookmark(tempBookmark.getId());
                                        itemPendingDeleteDecision = false;
                                        undoDeleteBookmarkSB.dismiss();
                                    }

                                    deleteCell(parentView, position);
                                    break;
                            }

                            return true;
                        }
                    });
                    popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                        @Override
                        public void onDismiss(PopupMenu popupMenu) {
                            overflowButton.setBackground(context.getResources().getDrawable(R.drawable.menu_overflow_fade));
                        }
                    });
                }
            });

            holder.bookmarkNoteBTN.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    ArrayList<ObjectAnimator> arrayListObjectAnimators = new ArrayList<ObjectAnimator>();
                    Animator[] objectAnimators;

                    RelativeLayout motherView = (RelativeLayout) view.getParent();
                    TextView bookmarkNoteTV = (TextView) motherView.getChildAt(0);
                    ImageView bookmarkIMG = (ImageView) motherView.getChildAt(1);
                    TextView bookmarkName = (TextView) motherView.getChildAt(2);
                    Button bookmarkAction = (Button) motherView.getChildAt(3);
                    TextView bookmarkViews = (TextView) motherView.getChildAt(5);

                    int isNoteShowing = bookmarks.get(position).getIsNoteShowing();

                    //Note was showing, hide
                    if (isNoteShowing == 1) {
                        view.setBackground(context.getResources().getDrawable(R.drawable.gray_bookmark));

                        ((GradientDrawable) ((LayerDrawable) motherView.getBackground()).findDrawableByLayerId(R.id.innerView)).setColor(context.getResources().getColor(R.color.white));

                        arrayListObjectAnimators.add(helperMethods.hideViewElement(bookmarkNoteTV));
                        arrayListObjectAnimators.add(helperMethods.showViewElement(bookmarkAction));
                        arrayListObjectAnimators.add(helperMethods.showViewElement(bookmarkIMG));
                        arrayListObjectAnimators.add(helperMethods.showViewElement(bookmarkViews));
                        arrayListObjectAnimators.add(helperMethods.showViewElement(bookmarkName));

                        bookmarks.get(position).setIsNoteShowing(0);
                    } else {
                        view.setBackground(context.getResources().getDrawable(R.drawable.white_bookmark));

                        ((GradientDrawable) ((LayerDrawable) motherView.getBackground()).findDrawableByLayerId(R.id.innerView)).setColor(context.getResources().getColor(helperMethods.determineNoteViewBackground(book_color_code)));

                        bookmarkNoteTV.setText(bookmarks.get(position).getNote());

                        arrayListObjectAnimators.add(helperMethods.showViewElement(bookmarkNoteTV));
                        arrayListObjectAnimators.add(helperMethods.hideViewElement(bookmarkAction));
                        arrayListObjectAnimators.add(helperMethods.hideViewElement(bookmarkIMG));
                        arrayListObjectAnimators.add(helperMethods.hideViewElement(bookmarkViews));
                        arrayListObjectAnimators.add(helperMethods.hideViewElement(bookmarkName));

                        bookmarks.get(position).setIsNoteShowing(1);
                    }

                    objectAnimators = arrayListObjectAnimators
                            .toArray(new ObjectAnimator[arrayListObjectAnimators
                                    .size()]);
                    AnimatorSet hideClutterSet = new AnimatorSet();
                    hideClutterSet.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            view.setEnabled(false);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            view.setEnabled(true);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                    hideClutterSet.playTogether(objectAnimators);
                    hideClutterSet.setDuration(200);
                    hideClutterSet.start();
                }
            });

            return parentView;
        }

        @DebugLog
        public void swap(int from, int to) {
            if (to < bookmarks.size() && from < bookmarks.size()) {
                Collections.swap(bookmarks, from, to);
                int tempNumber = bookmarks.get(from).getOrder();
                bookmarks.get(from).setOrder(bookmarks.get(to).getOrder());
                bookmarks.get(to).setOrder(tempNumber);
                dbHelper.updateBookmark(bookmarks.get(from));
                dbHelper.updateBookmark(bookmarks.get(to));
            }
        }
    }

    public static class BookmarksViewHolder {
        RelativeLayout motherView;
        AutofitTextView bookmarkName;
        ImageView bookmarkIMG;
        Button bookmarkAction;
        TextView bookmarkViews;
        Button bookmarkNoteBTN;
        AutofitTextView bookmarkNoteTV;
        boolean needInflate;
    }
}
