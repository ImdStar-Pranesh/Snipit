package com.om.snipit.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.nispok.snackbar.listeners.EventListener;
import com.om.snipit.R;
import com.om.snipit.classes.CircleTransform;
import com.om.snipit.classes.Constants;
import com.om.snipit.classes.DatabaseHelper;
import com.om.snipit.classes.EventBus_Poster;
import com.om.snipit.classes.EventBus_Singleton;
import com.om.snipit.classes.Helper_Methods;
import com.om.snipit.dragsort_listview.DragSortListView;
import com.om.snipit.models.Book;
import com.om.snipit.models.Snippet;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import net.frakbot.jumpingbeans.JumpingBeans;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import hugo.weaving.DebugLog;
import me.grantland.widget.AutofitTextView;

public class Books_Activity extends ActionBarActivity {

    @InjectView(R.id.booksList)
    DragSortListView listView;
    @InjectView(R.id.emptyListLayout)
    RelativeLayout emptyListLayout;
    @InjectView(R.id.createNewBookBTN)
    FloatingActionButton createNewBookBTN;
    @InjectView(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @InjectView(R.id.navDrawer)
    NavigationView navDrawer;
    @InjectView(R.id.navdrawer_header_user_profile_image)
    ImageView navdrawer_header_user_profile_image;
    @InjectView(R.id.navdrawer_header_user_full_name)
    TextView navdrawer_header_user_full_name;
    @InjectView(R.id.navdrawer_header_user_email)
    TextView navdrawer_header_user_email;
    @InjectView(R.id.toolbar)
    Toolbar toolbar;

    private SharedPreferences prefs;

    private ActionBarDrawerToggle drawerToggle;

    private Books_Adapter booksAdapter;
    private List<Book> books;
    private Book tempBook;

    private DatabaseHelper databaseHelper = null;
    private RuntimeExceptionDao<Book, Integer> bookDAO;
    private RuntimeExceptionDao<Snippet, Integer> snippetDAO;

    private QueryBuilder<Book, Integer> bookQueryBuilder;
    private QueryBuilder<Snippet, Integer> snippetQueryBuilder;
    private PreparedQuery<Snippet> pq;
    private PreparedQuery<Book> pqBook;

    private Snackbar undoDeleteBookSB;
    private boolean itemPendingDeleteDecision = false;

    private DragSortListView.DropListener onDrop =
            new DragSortListView.DropListener() {
                @Override
                public void drop(int from, int to) {
                    booksAdapter.notifyDataSetChanged();
                }
            };

    private DragSortListView.DragListener onDrag = new DragSortListView.DragListener() {
        @Override
        public void drag(int from, int to) {
            booksAdapter.swap(from, to);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books);

        EventBus_Singleton.getInstance().register(this);

        Helper_Methods helperMethods = new Helper_Methods(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        bookDAO = getHelper().getBookDAO();
        snippetDAO = getHelper().getSnippetDAO();

        bookQueryBuilder = bookDAO.queryBuilder();
        snippetQueryBuilder = snippetDAO.queryBuilder();

        ButterKnife.inject(this);

        prepareQueryBuilder();

        books = bookDAO.query(pqBook);

        handleEmptyOrPopulatedScreen();

        setSupportActionBar(toolbar);
        helperMethods.setUpActionbarColors(this, Constants.DEFAULT_ACTIVITY_TOOLBAR_COLORS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(25f);
        } else {
            listView.setDrawSelectorOnTop(true);
            listView.setSelector(R.drawable.abc_list_selector_holo_dark);
        }

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_closed) {
            public void onDrawerClosed(View view) {
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                invalidateOptionsMenu();
            }
        };

        drawerLayout.setDrawerListener(drawerToggle);
        drawerLayout.closeDrawer(GravityCompat.START);

        if (prefs.getBoolean(Constants.USER_LOGGED_IN, false)) {
            Picasso.with(this).load(prefs.getString(Constants.USER_PHOTO_URL, "")).fit().transform(new CircleTransform()).into(navdrawer_header_user_profile_image);
            navdrawer_header_user_full_name.setText(prefs.getString(Constants.USER_FULL_NAME, ""));
            navdrawer_header_user_email.setText(prefs.getString(Constants.USER_EMAIL_ADDRESS, ""));
        } else {
            Picasso.with(this).load(R.drawable.ic_launcher).fit().into(navdrawer_header_user_profile_image);
            navdrawer_header_user_full_name.setText(R.string.app_name);
            navdrawer_header_user_email.setText(R.string.app_tagline);
        }

        navDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.navigation_drawer_item_snippets_global_stream:
                        Intent openSnippetStreamActivity = new Intent(Books_Activity.this, Snippet_Stream_Activity.class);
                        startActivity(openSnippetStreamActivity);
                        break;
                    case R.id.navigation_drawer_item_settings:
                        Intent openSettingsIntent = new Intent(Books_Activity.this, Settings_Activity.class);
                        startActivity(openSettingsIntent);

                        break;
                    default:
                        return true;
                }

                return true;
            }
        });

        createNewBookBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent openCreateBookActivity = new Intent(Books_Activity.this, Create_Book_Activity.class);
                startActivity(openCreateBookActivity);
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Intent openSnippetsForBook = new Intent(Books_Activity.this, Snippets_Activity.class);
                Book book = (Book) listView.getItemAtPosition(position);

                //Clicking on an adview when there's no Internet connection will cause this condition to be satisfied because no Book will be found at the index of that adview
                if (book != null) {
                    openSnippetsForBook.putExtra(Constants.EXTRAS_BOOK, book);
                    startActivity(openSnippetsForBook);
                }
            }
        });
    }

    public DatabaseHelper getHelper() {
        if (databaseHelper == null) {
            databaseHelper =
                    OpenHelperManager.getHelper(this, DatabaseHelper.class);
        }

        return databaseHelper;
    }

    public void prepareQueryBuilder() {
        try {
            bookQueryBuilder.where().not().eq("title", "null");
            bookQueryBuilder.orderBy("order", true);
            pqBook = bookQueryBuilder.prepare();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            else
                super.onBackPressed();

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Subscribe
    public void handle_BusEvents(EventBus_Poster ebp) {
        String ebpMessage = ebp.getMessage();

        switch (ebpMessage) {
            case "book_added":
                handleBusEvents_ListRefresher();
                Log.d("EVENTS", "book_added - Books_Activity");
                break;
            case "snippet_added_books_activity":
                handleBusEvents_ListRefresher();
                Log.d("EVENTS", "snippet_added_books_activity - Books_Activity");
                break;
            case "snippet_deleted_books_activity":
                handleBusEvents_ListRefresher();
                Log.d("EVENTS", "snippet_deleted_books_activity - Books_Activity");
                break;
        }
    }

    public void handleBusEvents_ListRefresher() {
        prepareForNotifyDataChanged();
        booksAdapter.notifyDataSetChanged();
    }

    @DebugLog
    public void prepareForNotifyDataChanged() {
        books = bookDAO.query(pqBook);
        handleEmptyUI();
    }

    @DebugLog
    public void handleEmptyOrPopulatedScreen() {
        handleEmptyUI();

        booksAdapter = new Books_Adapter(this);

        listView.setDropListener(onDrop);
        listView.setDragListener(onDrag);

//        final View listViewHeaderAd = View.inflate(this, R.layout.books_list_adview_footer, null);
//        AdView mAdView = (AdView) listViewHeaderAd.findViewById(R.id.adView);
//        AdRequest adRequest = new AdRequest.Builder().build();
//        mAdView.loadAd(adRequest);

//        listView.addFooterView(listViewHeaderAd);
        listView.setAdapter(booksAdapter);
    }

    public void handleEmptyUI() {
        if (bookDAO.queryForAll().isEmpty()) {
            emptyListLayout.setVisibility(View.VISIBLE);
            JumpingBeans.with((TextView) emptyListLayout.findViewById(R.id.emptyLayoutMessageTV)).appendJumpingDots().build();
        } else if (bookDAO.queryForAll().isEmpty()) {
            emptyListLayout.setVisibility(View.GONE);
        } else {
            emptyListLayout.setVisibility(View.INVISIBLE);
        }
    }

    public void showUndeleteDialog(final Book tempBookToDelete) {
        itemPendingDeleteDecision = true;

        tempBook = tempBookToDelete;

        Spannable sentenceToSpan = new SpannableString(getResources().getString(R.string.delete_book_confirmation_message) + " " + tempBookToDelete.getTitle());

        sentenceToSpan.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        books.remove(tempBookToDelete);
        handleEmptyUI();
        booksAdapter.notifyDataSetChanged();

        undoDeleteBookSB =
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
                                    finalizeBookDeletion(tempBookToDelete);
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
                        prepareForNotifyDataChanged();
                        booksAdapter.notifyDataSetChanged();

                        itemPendingDeleteDecision = false;
                        undoDeleteBookSB.dismiss();
                    }
                });

        undoDeleteBookSB.show(Books_Activity.this);
    }

    public void finalizeBookDeletion(Book tempBook) {
        snippetDAO.delete(snippetDAO.queryForEq("book_id", tempBook.getId()));
        bookDAO.delete(tempBook);
        prepareForNotifyDataChanged();
        booksAdapter.notifyDataSetChanged();
        itemPendingDeleteDecision = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (itemPendingDeleteDecision) {

            finalizeBookDeletion(tempBook);

            if (undoDeleteBookSB.isShowing()) {
                undoDeleteBookSB.dismiss();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus_Singleton.getInstance().unregister(this);
    }

    public class Books_Adapter extends BaseAdapter {

        private LayoutInflater inflater;
        private Context context;
        private BooksViewHolder holder;
        private List<Snippet> snippets;
        private ProgressDialog uploadingSnippets_AWS;

        public Books_Adapter(Context context) {
            super();
            this.context = context;

            this.inflater = (LayoutInflater) context
                    .getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return books.size();
        }

        @Override
        public Object getItem(int i) {
            return books.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            final View parentView;

            if (convertView == null || ((BooksViewHolder) convertView.getTag()).needInflate) {
                parentView = inflater.inflate(R.layout.list_item_book, parent, false);

                holder = new BooksViewHolder();

                holder.list_item_book = (RelativeLayout) parentView.findViewById(R.id.list_item_book);
                holder.bookDateAddedTV = (TextView) parentView.findViewById(R.id.bookDateAddedTV);
                holder.bookTitleTV = (AutofitTextView) parentView.findViewById(R.id.bookTitleTV);
                holder.bookAuthorTV = (AutofitTextView) parentView.findViewById(R.id.bookAuthorTV);
                holder.bookThumbIMG = (ImageView) parentView.findViewById(R.id.bookThumbIMG);
                holder.snippetsNumberTV = (TextView) parentView.findViewById(R.id.snippetsNumberTV);
                holder.bookActionLayout = (LinearLayout) parentView.findViewById(R.id.bookActionLayout);
                holder.needInflate = false;

                parentView.setTag(holder);
            } else {
                parentView = convertView;
            }

            holder = (BooksViewHolder) parentView.getTag();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.snippetsNumberTV.setElevation(5f);
            }

            holder.bookTitleTV.setText(books.get(position).getTitle());
            holder.bookAuthorTV.setText(books.get(position).getAuthor());

//            Picasso.with(Books_Activity.this).load(books.get(position).getImagePath()).error(getResources().getDrawable(R.drawable.notfound_1)).into(holder.bookThumbIMG);
            Picasso.with(Books_Activity.this).load(books.get(position).getImagePath()).into(holder.bookThumbIMG);

            String[] bookDateAdded = books.get(position).getDate_added().split(" ");
            holder.bookDateAddedTV.setText(bookDateAdded[0] + " " + bookDateAdded[1] + ", " + bookDateAdded[2]);

            switch (books.get(position).getColorCode()) {
                case 0:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_pink));
                    break;
                case 1:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_red));
                    break;
                case 2:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_purple));
                    break;
                case 3:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_yellow));
                    break;
                case 4:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_blue));
                    break;
                case 5:
                    holder.snippetsNumberTV.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.snippet_brown));
                    break;
            }

            holder.bookActionLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final View overflowButton = view.findViewById(R.id.bookAction);
                    overflowButton.findViewById(R.id.bookAction).setBackgroundDrawable(context.getResources().getDrawable(R.drawable.menu_overflow_focus));

                    PopupMenu popup = new PopupMenu(context, view);
                    popup.getMenuInflater().inflate(R.menu.book_list_item,
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
//                                case R.id.share:
//                                    uploadingSnippets_AWS = new ProgressDialog(Books_Activity.this);
//                                    if (snippets.size() == 1)
//                                        uploadingSnippets_AWS.setMessage("Uploading " + snippets.size() + " image to AWS");
//                                    else
//                                        uploadingSnippets_AWS.setMessage("Uploading " + snippets.size() + " images to AWS");
//                                    uploadingSnippets_AWS.setIndeterminate(false);
//                                    uploadingSnippets_AWS.setMax(snippets.size());
//                                    uploadingSnippets_AWS.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//                                    uploadingSnippets_AWS.setCancelable(true);
//                                    uploadingSnippets_AWS.show();
//
//                                    final AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(Constants.AMAZON_ACCESS_KEY, Constants.AMAZON_SECRET_ACCESS_KEY));

//                                    Thread t = new Thread(new Runnable() {
//                                        @Override
//                                        public void run() {
//
//                                            for (final Snippet snippet : snippets) {
//
//                                                PutObjectRequest por = new PutObjectRequest("snippet-images", snippet.getName(), new File(snippet.getImage_path()));
//                                                por.setGeneralProgressListener(new ProgressListener() {
//                                                    @Override
//                                                    public void progressChanged(ProgressEvent progressEvent) {
//
//                                                        if (progressEvent.getEventCode() == ProgressEvent.COMPLETED_EVENT_CODE) {
//                                                            uploadingSnippets_AWS.setProgress(uploadingSnippets_AWS.getProgress() + 1);
//
//                                                            Log.d("PROGRESS", "COMPLETED");
//
//                                                            ResponseHeaderOverrides override = new ResponseHeaderOverrides();
//                                                            override.setContentType("image/jpeg");
//                                                            GeneratePresignedUrlRequest urlRequest = new GeneratePresignedUrlRequest("snippet-images", snippet.getName());
//                                                            urlRequest.setResponseHeaders(override);
//                                                            URL url = s3Client.generatePresignedUrl(urlRequest);
//                                                            try {
//                                                                Log.d("URL", "FINAL URL OF AWS IMAGE is : " + Uri.parse(url.toURI().toString()).toString());
//                                                                snippet.setAWS_image_path(Uri.parse(url.toURI().toString()).toString());
//                                                            } catch (URISyntaxException e) {
//                                                                e.printStackTrace();
//                                                            }
//
//                                                            if (uploadingSnippets_AWS.getProgress() == uploadingSnippets_AWS.getMax())
//                                                                uploadingSnippets_AWS.dismiss();
//                                                        }
//                                                    }
//                                                });
//
//                                                s3Client.putObject(por);
//                                            }
//                                        }
//                                    });
//                                    t.start();
//                                    break;
                                case R.id.edit:
                                    Intent editBookIntent = new Intent(Books_Activity.this, Create_Book_Activity.class);
                                    editBookIntent.putExtra(Constants.EXTRAS_BOOK, books.get(position));
                                    editBookIntent.putExtra(Constants.EDIT_BOOK_PURPOSE_STRING, Constants.EDIT_BOOK_PURPOSE_VALUE);
                                    startActivity(editBookIntent);
                                    break;
                                case R.id.delete:
                                    //Dissmiss the UNDO Snackbar and handle the deletion of the previously awaiting item yourself
                                    if (undoDeleteBookSB != null && undoDeleteBookSB.isShowing()) {
                                        //Careful about position that is passed from the adapter! This has to be accounted for again by using getItemAtPosition because there's an adview among the views
                                        //I am able to use tempBook h=ere because I am certain that it would have now been initialized inside deleteCell(), no way to reach this point without having been through deleteCell() first

                                        try {
                                            snippetQueryBuilder.where().eq("book_id", tempBook.getId());
                                            pq = snippetQueryBuilder.prepare();
                                            snippetDAO.delete(snippetDAO.query(pq));
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        bookDAO.delete(tempBook);
                                        itemPendingDeleteDecision = false;
                                        undoDeleteBookSB.dismiss();
                                    }

                                    showUndeleteDialog(books.get(position));

                                    break;
                            }

                            return true;
                        }
                    });
                    popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                        @Override
                        public void onDismiss(PopupMenu popupMenu) {
                            overflowButton.findViewById(R.id.bookAction).setBackgroundDrawable(context.getResources().getDrawable(R.drawable.menu_overflow_fade));
                        }
                    });
                }
            });

            snippets = snippetDAO.queryForEq("book_id", books.get(position).getId());
            holder.snippetsNumberTV.setText(snippets.size() + "");

            return parentView;
        }

        @DebugLog
        public void swap(int from, int to) {
            if (to < books.size() && from < books.size()) {
                Collections.swap(books, from, to);
                int tempNumber = books.get(from).getOrder();
                books.get(from).setOrder(books.get(to).getOrder());
                books.get(to).setOrder(tempNumber);
                bookDAO.update(books.get(from));
                bookDAO.update(books.get(to));
            }
        }
    }

    public static class BooksViewHolder {
        RelativeLayout list_item_book;
        TextView bookDateAddedTV;
        AutofitTextView bookTitleTV;
        AutofitTextView bookAuthorTV;
        ImageView bookThumbIMG;
        TextView snippetsNumberTV;
        LinearLayout bookActionLayout;
        boolean needInflate;
    }
}
